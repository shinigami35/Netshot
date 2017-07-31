package onl.netfishers.netshot.scp.watcher;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.RestService;
import onl.netfishers.netshot.http.MappingHttp;
import onl.netfishers.netshot.scp.device.ScpStepFolder;
import onl.netfishers.netshot.scp.device.VirtualDevice;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static onl.netfishers.netshot.scp.device.VirtualDevice.DEFAULT_FOLDER;
import static onl.netfishers.netshot.scp.device.VirtualDevice.setPermFolder;
import static onl.netfishers.netshot.scp.job.JobTools.*;

/**
 * Created by agm on 18/05/2017.
 */
public class Watcher extends Thread {

    private WatchService watcher;
    private Map<WatchKey, Path> keys;
    private boolean recursive = true;
    private boolean trace = false;

    /**
     * The logger.
     */
    private Logger logger = LoggerFactory.getLogger(Watcher.class);
    private String destPath = Netshot.getConfig("netshot.watch.moveFile");

    public Watcher() {
        super("Watcher");
    }


    private boolean createDestDirectory(String destPath) {
        Path dest = Paths.get(destPath);
        try {
            if (!Files.exists(dest)) {
                Files.createDirectories(dest);
                setPermFolder(dest);
            } else if (Files.exists(dest) && !Files.isDirectory(dest)) {
                Files.createDirectories(dest);
                setPermFolder(dest);
            }
            return true;
        } catch (IOException e) {
            logger.error("Cannot create directories : " + dest.toAbsolutePath().toString(), e);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    public void run() {
        try {
            init();
        } catch (IOException e) {
            logger.error("Cannot put a watcher ", e);
        }
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    public void init() throws IOException {
        String s = Netshot.getConfig("netshot.watch.folderListen");
        Path dir = Paths.get(s + '/' + DEFAULT_FOLDER);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
            setPermFolder(dir);
        } else if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            setPermFolder(dir);
        }

        System.out.println("Path watcher is : " + dir);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            watcher = FileSystems.getDefault().newWatchService();
            keys = new HashMap<WatchKey, Path>();

            Session session = Database.getSession();

            List listDir = session.createCriteria(VirtualDevice.class).list();
            for (Object o : listDir) {
                VirtualDevice vd = (VirtualDevice) o;
                Path p = Paths.get(s + '/' + vd.getFolder());
                register(p);
            }
            register(dir);
            session.close();

            System.out.format("Scan done with %d folders loaded\n", keys.size());
            System.out.println("Watch on directory for new existing file");
            getNewInstanceOnReload();
            System.out.println("Watcher launch !");
            processEvents();
            trace = true;
        }
    }

    /**
     * Process all events for keys queued to the watcher
     */
    private void processEvents() {
        String s = Netshot.getConfig("netshot.watch.folderListen");
        Path dirRoot = Paths.get(s + '/' + DEFAULT_FOLDER);
        while (true) {

            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    System.out.println("Overflow :/");
                    continue;
                }


                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            String parentChild = child.getParent().toAbsolutePath().toString();
                            String sdirRoot = dirRoot.toAbsolutePath().toString();
                            if (parentChild.equals(sdirRoot))
                                register(child);
                        } else {
                            System.out.println("File is => " + child);
                            while (true) {
                                if (copyCompleted(child))
                                    break;
                            }
                            saveEntryScp(child);
                        }
                    } catch (IOException x) {
                        System.out.println("Cannot open the file => " + x);
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void saveEntryScp(Path child) {
        Session session = Database.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            List l = session.createCriteria(VirtualDevice.class).list();
            if (l.size() > 0) {
                String firstPath = Netshot.getConfig("netshot.watch.folderListen");
                for (Object o : l) {
                    VirtualDevice vd = (VirtualDevice) o;
                    String tmpPath = firstPath + vd.getFolder();
                    Path tmp = Paths.get(tmpPath);
                    if (child.getParent().equals(tmp)) {
                        ScpStepFolder s = new ScpStepFolder();
                        BasicFileAttributes attr = Files.readAttributes(child, BasicFileAttributes.class);
                        s.setSize(attr.size());
                        s.setHumanSize(humanReadableByteCount(attr.size(), true));
                        s.setNameFile(child.getFileName().toString());
                        s.setCreated_at(convertDate(new Date(attr.creationTime().toMillis())));
                        s.setVirtual(vd);
                        s.setStatus(ScpStepFolder.TaskStatus.SUCCESS);
                        s.setCreated(new Date(attr.creationTime().toMillis()));

                        session.save(s);
                        session.update(vd);
                        tx.commit();

                        moveFile(vd, child.getFileName().toString(), s.getCreated());

                        break;
                    }
                }
            }
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error while save new Scp Entry.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Error while save new Scp Entry.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (IOException e) {
            logger.error("Error while open file => " + child, e);
        } finally {
            session.close();
        }
    }

    private String convertDate(Date d) {
        SimpleDateFormat timeStamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);
        return timeStamp.format(d);
    }

    private void moveFile(VirtualDevice vs, String nameFile, Date d) {
        String folder = vs.getFolder();
        Path src = generatePathDest(vs.getFolder());
        Path dest = generatePath(destPath, folder);

        if (createDestDirectory(destPath)) {
            try {
                if (createDestDirectory(dest.toAbsolutePath().toString())) {
                    Path tmpSrc = Paths.get(src.toAbsolutePath().toString() + '/' + nameFile);
                    Path tmpDest = Paths.get(dest.toAbsolutePath().toString() + '/' +
                            vs.getId() + "_" + generateDateSave(d) + '_' + nameFile);
                    Files.move(tmpSrc, tmpDest, REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("Cannot create directories : " + dest.toAbsolutePath().toString(), e);
            }
        }
    }

    private boolean copyCompleted(Path filePath) {

        File ff = filePath.toFile();

        boolean copyCompleted = false;
        FileInputStream fis;

        if (ff.exists()) {
            int ref = 0;
            try {
                fis = new FileInputStream(ff);
                while (true) {
                    if (ref != 0 && ref == fis.available()) {
                        copyCompleted = true;
                        break;
                    } else
                        ref = fis.available();
                    Thread.sleep(1000);
                }
            } catch (IOException e) {
                logger.error("Could not open " + ff.getName(), e);
            } catch (InterruptedException e) {
                logger.error("Thread sleep error " + ff.getName(), e);
            }
        }
        return copyCompleted;
    }

    private void getNewInstanceOnReload() {
        Session session = Database.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            List virt = session.createCriteria(VirtualDevice.class).list();

            for (Object o : virt) {
                VirtualDevice vd = (VirtualDevice) o;
                File folder = generatePathDest(vd.getFolder()).toFile();
                File[] listOfFiles = folder.listFiles();
                if (listOfFiles != null && listOfFiles.length > 0) {
                    for (File f : listOfFiles) {
                        Path child = f.toPath();
                        ScpStepFolder s = new ScpStepFolder();
                        BasicFileAttributes attr = Files.readAttributes(child, BasicFileAttributes.class);
                        s.setSize(attr.size());
                        s.setHumanSize(humanReadableByteCount(attr.size(), true));
                        s.setNameFile(child.getFileName().toString());
                        s.setCreated_at(convertDate(new Date(attr.creationTime().toMillis())));
                        s.setVirtual(vd);
                        s.setStatus(ScpStepFolder.TaskStatus.SUCCESS);
                        s.setCreated(new Date(attr.creationTime().toMillis()));

                        session.save(s);
                        session.update(vd);
                        moveFile(vd, child.getFileName().toString(), s.getCreated());
                    }
                }
            }
            tx.commit();
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error access BDD ", e);
        } catch (IOException e) {
            logger.error("Error access file in directory", e);
        }
    }
}