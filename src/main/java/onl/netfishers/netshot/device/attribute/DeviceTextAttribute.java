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
package onl.netfishers.netshot.device.attribute;

import onl.netfishers.netshot.device.Device;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlElement;

@Entity
@DiscriminatorValue("S")
public class DeviceTextAttribute extends DeviceAttribute {

    private String text;

    protected DeviceTextAttribute() {
    }

    public DeviceTextAttribute(Device device, String name, String value) {
        super(device, name);
        this.text = value;
    }

    @XmlElement
    public String getText() {
        return text;
    }

    public void setText(String value) {
        this.text = value;
    }

    @Override
    @Transient
    public Object getData() {
        return getText();
    }

}
