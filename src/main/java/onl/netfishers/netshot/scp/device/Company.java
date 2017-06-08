package onl.netfishers.netshot.scp.device;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;


@Entity
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class Company implements Serializable {

    private long id;

    private String name;

    private Set<VirtualDevice> appliances = new HashSet<>(0);

    public Company() {

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
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public Set<VirtualDevice> getAppliances() {
        return appliances;
    }

    public void setAppliances(Set<VirtualDevice> appliances) {
        this.appliances = appliances;
    }
}
