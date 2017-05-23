package onl.netfishers.netshot.scp;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.RestService;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Created by agm on 18/05/2017.
 */
public class Watcher {

    private static WatchService watcher;
    private static Map<WatchKey, Path> keys;
    private static boolean recursive;
    private static boolean trace = false;

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(Watcher.class);

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private static void register(Path dir) throws IOException {
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
    private static void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Watcher.register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    public static void init() throws IOException {
        String s = Netshot.getConfig("netshot.watch.folderListen");
        Path dir = Paths.get(s);
        System.out.println("Path watcher is : " + dir);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            System.out.println("Set the watcher");
            watcher = FileSystems.getDefault().newWatchService();
            keys = new HashMap<WatchKey, Path>();
            recursive = true;

            if (recursive) {
                System.out.format("Scanning %s ...\n", dir);
                registerAll(dir);
                Watcher.processEvents();
                System.out.println("Done.");
            } else {
                register(dir);
            }
            trace = true;
        }
    }

    /**
     * Process all events for keys queued to the watcher
     */
    private static void processEvents() {
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
                    continue;
                }


                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        } else {
                            System.out.println("File is => " + child);
                            saveEntryScp(child);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readbale
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

    private static void saveEntryScp(Path child) {
        Session session = Database.getSession();
        Transaction tx;
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
                        s.setSize(getInMb(attr.size()));
                        s.setNameFile(child.getFileName().toString());
                        s.setCreated_at(convertDate(new Date(attr.creationTime().toMillis())));
                        s.setVirtual(vd);
                        session.save(s);
                        tx.commit();
                        break;
                    }
                }
            }
        } catch (HibernateException e) {
            logger.error("Error while save new Scp Entry.", e);
            throw new RestService.NetshotBadRequestException(
                    "Error while save new Scp Entry.",
                    RestService.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (IOException e) {
            logger.error("Error while open file => " + child, e);
        } finally {
            session.close();
        }
    }

    private static long getInMb(long sizeInBytes) {
        return sizeInBytes / (1024 * 1024);
    }

    private static String convertDate(Date d) {
        SimpleDateFormat timeStamp = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss", Locale.FRANCE);
        return timeStamp.format(d);
    }
}
