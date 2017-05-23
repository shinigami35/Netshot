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

    protected VirtualDevice() {
    }

    public VirtualDevice(Types type, Company company, String folder) {
        this.type = type;
        this.company = company;
        this.folder = folder;
    }

    public VirtualDevice(Long id, Types type, Company company, String folder) {
        this.type = type;
        this.company = company;
        this.folder = folder;
        this.id = id;
    }

    public VirtualDevice(String name, String folder) {
        this.folder = folder;
        this.name = name;
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
}
