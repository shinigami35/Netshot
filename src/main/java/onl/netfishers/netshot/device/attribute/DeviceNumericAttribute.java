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
@DiscriminatorValue("N")
public class DeviceNumericAttribute extends DeviceAttribute {

    private Double number;

    protected DeviceNumericAttribute() {
    }

    public DeviceNumericAttribute(Device device, String name, double value) {
        super(device, name);
        this.number = value;
    }

    @XmlElement
    public Double getNumber() {
        return number;
    }

    public void setNumber(Double value) {
        this.number = value;
    }

    @Override
    @Transient
    public Object getData() {
        return getNumber();
    }

}
