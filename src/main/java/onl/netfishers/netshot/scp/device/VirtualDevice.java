package onl.netfishers.netshot.scp.device;

import onl.netfishers.netshot.Netshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by agm on 16/05/2017.
 */

@Entity
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class VirtualDevice implements Serializable {

    public static String DEFAULT_FOLDER = "Netshot";
    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(VirtualDevice.class);
    /**
     * The id.
     */
    protected long id;
    /**
     * The type appliance
     */
    protected Types type;
    /**
     * The saved name
     */
    protected String name;
    /**
     * The saved folder
     */
    protected String folder;
    protected Company company;
    protected Set<ScpStepFolder> file = new HashSet<>();
    private CRON cron;

    private Date hour;


    protected VirtualDevice() {
    }

    public VirtualDevice(String name, String folder) {
        this.folder = DEFAULT_FOLDER + folder;
        this.name = name;
    }


    public static boolean createFolder(String folder) {
        String firstPath = Netshot.getConfig("netshot.watch.folderListen");
        String tmpPath;
        folder = folder.replaceAll("[^a-zA-Z0-9.-]", "_");
        if (firstPath.charAt(firstPath.length() - 1) == '/')
            tmpPath = firstPath + DEFAULT_FOLDER + '/' + folder;
        else
            tmpPath = firstPath + '/' + DEFAULT_FOLDER + '/' + folder;
        try {
            tmpPath = tmpPath.replaceAll("\\s","");
            Path tmp = Paths.get(tmpPath);
            if (Files.notExists(tmp))
                Files.createDirectories(tmp);
            else if (Files.exists(tmp) && !Files.isDirectory(tmp))
                Files.createDirectories(tmp);
            setPermFolder(tmp);
            return true;
        } catch (IOException e) {
            logger.error("Cannot create those directory : " + tmpPath, e);
        }
        return false;

    }

    public static void setPermFolder(Path p) {
        String group = Netshot.getConfig("netshot.watch.group");

        Set<PosixFilePermission> perms = new HashSet<>();
        try {
            FileSystem fileSystem = p.getFileSystem();
            UserPrincipalLookupService lookupService = fileSystem.getUserPrincipalLookupService();


            //add owners permission
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            //add group permissions
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);


            if (group != null && !group.equals("")) {
                GroupPrincipal groupPrincipal = lookupService.lookupPrincipalByGroupName(group);
                Files.getFileAttributeView(p, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setGroup(groupPrincipal);
                Files.setPosixFilePermissions(p, perms);
            }
        } catch (IOException e) {
            logger.error("Could not set perms to : " + p.getFileName().toString(), e);
        }
    }


    @Id
    @GeneratedValue
    @XmlAttribute
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @XmlElement
    @OneToOne(fetch = FetchType.EAGER)
    public Types getType() {
        return type;
    }

    public void setType(Types type) {
        this.type = type;
    }

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    @XmlElement
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "virtual")
    @OrderBy("created DESC")
    public Set<ScpStepFolder> getFile() {
        return file;
    }

    public void setFile(Set<ScpStepFolder> file) {
        this.file = file;
    }

    @XmlElement
    @OneToOne(fetch = FetchType.EAGER)
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    @XmlElement
    public CRON getCron() {
        return cron;
    }

    public void setCron(CRON cron) {
        this.cron = cron;
    }

    @XmlElement
    public Date getHour() {
        return hour;
    }

    public void setHour(Date hour) {
        this.hour = hour;
    }


    public enum CRON {
        DAILY,
        WEEKLY,
        HOUR
    }
}
