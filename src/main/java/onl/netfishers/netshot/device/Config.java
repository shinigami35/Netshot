/**
 * Copyright 2013-2016 Sylvain Cadilhac (NetFishers)
 * <p>
 * This file is part of Netshot.
 * <p>
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package onl.netfishers.netshot.device;

import onl.netfishers.netshot.device.attribute.ConfigAttribute;
import org.hibernate.annotations.Filter;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

/**
 * A device configuration.
 */
@Entity
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
@Table(indexes = {
        @Index(name = "changeDateIndex", columnList = "changeDate")
})
public class Config {

    /**
     * The change date.
     */
    protected Date changeDate;
    /**
     * The device.
     */
    protected Device device;
    /**
     * The id.
     */
    protected long id;
    /**
     * The attributes.
     */
    private Set<ConfigAttribute> attributes = new HashSet<ConfigAttribute>();
    /**
     * The author.
     */
    private String author = "";
    private int version;

    /**
     * Instantiates a new config.
     */
    public Config() {
    }

    /**
     * Instantiates a new config.
     *
     * @param device the device
     */
    public Config(Device device) {
        this.device = device;
    }

    public void addAttribute(ConfigAttribute attribute) {
        this.attributes.add(attribute);
    }

    public void clearAttributes() {
        attributes.clear();
    }

    @XmlElement
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "config", orphanRemoval = true)
    @Filter(name = "lightAttributesOnly")
    public Set<ConfigAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(Set<ConfigAttribute> attributes) {
        this.attributes = attributes;
    }

    @Transient
    public Map<String, ConfigAttribute> getAttributeMap() {
        Map<String, ConfigAttribute> map = new HashMap<String, ConfigAttribute>();
        for (ConfigAttribute a : this.attributes) {
            map.put(a.getName(), a);
        }
        return map;
    }

    /**
     * Gets the author.
     *
     * @return the author
     */
    @XmlElement
    public String getAuthor() {
        return author;
    }

    /**
     * Sets the author.
     *
     * @param author the new author
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Gets the change date.
     *
     * @return the change date
     */
    @XmlElement
    public Date getChangeDate() {
        return changeDate;
    }

    /**
     * Sets the change date.
     *
     * @param changeDate the new change date
     */
    public void setChangeDate(Date changeDate) {
        this.changeDate = changeDate;
    }

    @Version
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Gets the device.
     *
     * @return the device
     */
    @ManyToOne(fetch = FetchType.LAZY)
    public Device getDevice() {
        return device;
    }

    /**
     * Sets the device.
     *
     * @param device the new device
     */
    public void setDevice(Device device) {
        this.device = device;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    @XmlElement
    @Id
    @GeneratedValue
    public long getId() {
        return id;
    }

    /**
     * Sets the id.
     *
     * @param id the new id
     */
    public void setId(long id) {
        this.id = id;
    }

}
