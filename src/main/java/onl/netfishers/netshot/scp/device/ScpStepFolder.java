package onl.netfishers.netshot.scp.device;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by agm on 17/05/2017.
 */

@Entity
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class ScpStepFolder implements Serializable {

    protected long id;
    protected long size;
    protected VirtualDevice virtual = null;
    private String created_at = null;
    private String nameFile = "";
    private TaskStatus status;
    private String humanSize;
    private Date created;

    public ScpStepFolder() {
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
    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    @XmlElement
    public String getNameFile() {
        return nameFile;
    }

    public void setNameFile(String nameFile) {
        this.nameFile = nameFile;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    public VirtualDevice getVirtual() {
        return virtual;
    }

    public void setVirtual(VirtualDevice virtual) {
        this.virtual = virtual;
    }

    @XmlElement
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @XmlElement
    public String getHumanSize() {
        return humanSize;
    }

    public void setHumanSize(String humanSize) {
        this.humanSize = humanSize;
    }

    @XmlElement
    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @XmlElement
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public enum TaskStatus {
        SUCCESS,
        FAILED
    }
}
