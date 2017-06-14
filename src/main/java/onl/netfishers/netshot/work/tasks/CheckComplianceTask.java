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
package onl.netfishers.netshot.work.tasks;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.compliance.HardwareRule;
import onl.netfishers.netshot.compliance.Policy;
import onl.netfishers.netshot.compliance.SoftwareRule;
import onl.netfishers.netshot.compliance.SoftwareRule.ConformanceLevel;
import onl.netfishers.netshot.device.Device;
import onl.netfishers.netshot.work.Task;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.criterion.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * This task checks the configuration compliance of a device.
 */
@Entity
public class CheckComplianceTask extends Task {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(CheckComplianceTask.class);

    /** The device. */
    private Device device;

    /**
     * Instantiates a new check compliance task.
     */
    protected CheckComplianceTask() {
    }

    /**
     * Instantiates a new check compliance task.
     *
     * @param device the device
     * @param comments the comments
     */
    public CheckComplianceTask(Device device, String comments, String author) {
        super(comments, (device.getLastConfig() == null ? device.getMgmtAddress().getIp() : device.getName()), author);
        this.device = device;
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.work.Task#prepare()
     */
    @Override
    public void prepare() {
        Hibernate.initialize(device);
        Hibernate.initialize(device.getComplianceCheckResults());
        Hibernate.initialize(device.getComplianceExemptions());
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.work.Task#run()
     */
    @Override
    public void run() {
        logger.debug("Starting check compliance task for device {}.", device.getId());
        this.logIt(String.format("Check compliance task for device %s (%s).",
                device.getName(), device.getMgmtAddress().getIp()), 5);

        Session session = Database.getSession();
        try {
            session.beginTransaction();
            session
                    .createQuery("delete from CheckResult c where c.key.device.id = :id")
                    .setLong("id", this.device.getId())
                    .executeUpdate();
            session.evict(this.device);
            Device device = (Device) session
                    .createQuery("from Device d join fetch d.lastConfig where d.id = :id")
                    .setLong("id", this.device.getId()).uniqueResult();
            if (device == null) {
                logger.info("Unable to fetch the device with its last config... has it been captured at least once?");
                throw new Exception("No last config for this device. Has it been captured at least once?");
            }
            @SuppressWarnings("unchecked")
            List<Policy> policies = session
                    .createQuery("select p from Policy p join p.targetGroup g join g.cachedDevices d where d.id = :id")
                    .setLong("id", this.device.getId())
                    .list();

            for (Policy policy : policies) {
                policy.check(device, session);
                session.merge(policy);
            }
            @SuppressWarnings("unchecked")
            List<SoftwareRule> softwareRules = session.createCriteria(SoftwareRule.class)
                    .addOrder(Property.forName("priority").asc()).list();
            device.setSoftwareLevel(ConformanceLevel.UNKNOWN);
            for (SoftwareRule rule : softwareRules) {
                rule.check(device);
                if (device.getSoftwareLevel() != ConformanceLevel.UNKNOWN) {
                    break;
                }
            }
            @SuppressWarnings("unchecked")
            List<HardwareRule> hardwareRules = session.createCriteria(HardwareRule.class).list();
            device.resetEoX();
            for (HardwareRule rule : hardwareRules) {
                rule.check(device);
            }
            session.merge(device);
            session.getTransaction().commit();
            this.status = Status.SUCCESS;
        } catch (Exception e) {
            session.getTransaction().rollback();
            logger.error("Error while checking compliance.", e);
            this.logIt("Error while checking compliance: " + e.getMessage(), 2);
            this.status = Status.FAILURE;
            return;
        } finally {
            session.close();
        }


    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.work.Task#getTaskDescription()
     */
    @Override
    @XmlElement
    @Transient
    public String getTaskDescription() {
        return "Device compliance check";
    }

    /**
     * Gets the device.
     *
     * @return the device
     */
    @ManyToOne(fetch = FetchType.LAZY)
    protected Device getDevice() {
        return device;
    }

    /**
     * Sets the device.
     *
     * @param device the new device
     */
    protected void setDevice(Device device) {
        this.device = device;
    }

    /* (non-Javadoc)
     * @see onl.netfishers.netshot.work.Task#clone()
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        CheckComplianceTask task = (CheckComplianceTask) super.clone();
        task.setDevice(this.device);
        return task;
    }

}
