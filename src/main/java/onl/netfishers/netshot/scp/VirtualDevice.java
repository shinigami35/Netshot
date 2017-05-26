package onl.netfishers.netshot.scp;

import onl.netfishers.netshot.Netshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private String login = null;

    private String password = null;

    private String ip = null;


    protected VirtualDevice() {
    }

    public VirtualDevice(String name, String folder) {
        this.folder = folder;
        this.name = name;
    }


    public static boolean createFolder(String folder) {
        String firstPath = Netshot.getConfig("netshot.watch.folderListen");
        String tmpPath;
        if (firstPath.charAt(firstPath.length() - 1) == '/')
            tmpPath = firstPath + folder;
        else
            tmpPath = firstPath + '/' + folder;
        try {
            Path tmp = Paths.get(tmpPath);
            if (Files.notExists(tmp))
                Files.createDirectories(tmp);
            else if (Files.exists(tmp) && !Files.isDirectory(tmp))
                Files.createDirectories(tmp);
            return true;
        } catch (IOException e) {
            logger.error("Cannot create those directory : " + tmpPath, e);
        }
        return false;

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
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
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
    public Set<ScpStepFolder> getFile() {
        return file;
    }

    public void setFile(Set<ScpStepFolder> file) {
        this.file = file;
    }

    @XmlElement
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
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

    @XmlElement
    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    @XmlElement
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @XmlElement
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public enum CRON {
        DAILY,
        WEEKLY,
        HOUR
    }
}
