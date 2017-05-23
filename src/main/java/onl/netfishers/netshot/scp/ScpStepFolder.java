package onl.netfishers.netshot.scp;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by agm on 17/05/2017.
 */

@Entity
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class ScpStepFolder implements Serializable {

    protected long id;

    protected String created_at = null;

    protected String nameFile = "";

    protected long size;

    protected VirtualDevice virtual = null;

    protected ScpStepFolder() {
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
}
