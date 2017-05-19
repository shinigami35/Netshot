package onl.netfishers.netshot.scp;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.Serializable;
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
     * The name appliance
     */
    protected String name;

    /**
     * The appliance IP.
     */
    protected String ip;

    /**
     * The saved folder
     */
    protected String folder;

    protected Company company;

    protected Set<ScpStepFolder> file = new HashSet<>();

    protected VirtualDevice() {
    }

    public VirtualDevice(String name, String ip, Company company, String folder) {
        this.name = name;
        this.ip = ip;
        this.company = company;
        this.folder = folder;
    }

    public VirtualDevice(Long id, String name, String ip, Company company, String folder) {
        this.name = name;
        this.ip = ip;
        this.company = company;
        this.folder = folder;
        this.id = id;
    }

    public VirtualDevice(String name, String ip, String folder) {
        this.name = name;
        this.ip = ip;
        this.folder = folder;
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
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
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
    @OneToOne(cascade = CascadeType.ALL)
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
