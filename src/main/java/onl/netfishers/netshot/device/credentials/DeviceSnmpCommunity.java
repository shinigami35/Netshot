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
package onl.netfishers.netshot.device.credentials;

import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlElement;


/**
 * A SNMP community to poll a device.
 */
@Entity
public abstract class DeviceSnmpCommunity extends DeviceCredentialSet {

    /**
     * The community.
     */
    private String community;

    /**
     * Instantiates a new device snmp community.
     */
    protected DeviceSnmpCommunity() {

    }

    /**
     * Instantiates a new device snmp community.
     *
     * @param community the community
     * @param name      the name
     */
    public DeviceSnmpCommunity(String community, String name) {
        super(name);
        this.community = community;
    }

    /**
     * Gets the community.
     *
     * @return the community
     */
    @XmlElement
    public String getCommunity() {
        return community;
    }

    /**
     * Sets the community.
     *
     * @param community the new community
     */
    public void setCommunity(String community) {
        this.community = community;
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.device.credentials.DeviceCredentialSet#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.device.credentials.DeviceCredentialSet#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof DeviceSnmpCommunity)) {
            return false;
        }
        DeviceSnmpCommunity other = (DeviceSnmpCommunity) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

}
