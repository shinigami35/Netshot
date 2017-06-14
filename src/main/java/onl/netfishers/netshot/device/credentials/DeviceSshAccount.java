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
import javax.xml.bind.annotation.XmlRootElement;


/**
 * An SSH account.
 */
@Entity
@XmlRootElement()
public class DeviceSshAccount extends DeviceCliAccount {

    /**
     * Instantiates a new device ssh account.
     */
    protected DeviceSshAccount() {

    }

    /**
     * Instantiates a new device ssh account.
     *
     * @param username      the username
     * @param password      the password
     * @param superPassword the super password
     * @param name          the name
     */
    public DeviceSshAccount(String username, String password,
                            String superPassword, String name) {
        super(username, password, superPassword, name);
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.device.credentials.DeviceCliAccount#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.device.credentials.DeviceCliAccount#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof DeviceSshAccount)) {
            return false;
        }
        DeviceSshAccount other = (DeviceSshAccount) obj;
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
