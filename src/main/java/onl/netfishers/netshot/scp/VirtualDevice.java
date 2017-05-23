package onl.netfishers.netshot.scp;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.Serializable;
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

    private TaskScp lastTask;

    protected VirtualDevice() {
    }

    public VirtualDevice(String name, String folder) {
        this.folder = folder;
        this.name = name;
    }


    public VirtualDevice(String name, String folder, Date date, CRON cron) {
        this.folder = folder;
        this.name = name;
        this.cron = cron;
        this.hour = date;
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
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public TaskScp getLastTask() {
        return lastTask;
    }

    public void setLastTask(TaskScp lastTask) {
        this.lastTask = lastTask;
    }

    public enum CRON {
        DAILY,
        WEEKLY,
        HOUR
    }
}
