package onl.netfishers.netshot.scp;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.util.Date;

/**
 * Created by agm on 23/05/2017.
 */

@Entity
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class TaskScp {

    private long id;
    private Date date;
    private TaskStatus status;
    private ScpStepFolder scpStepFolder;

    public TaskScp() {
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
    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @XmlElement
    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @XmlElement
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public ScpStepFolder getScpStepFolder() {
        return scpStepFolder;
    }

    public void setScpStepFolder(ScpStepFolder scpStepFolder) {
        this.scpStepFolder = scpStepFolder;
    }

    public enum TaskStatus {
        SUCCESS,
        FAILED
    }
}
