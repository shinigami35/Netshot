package onl.netfishers.netshot.scp.job;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.scp.device.VirtualDevice;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static onl.netfishers.netshot.scp.job.JobTools.*;

/**
 * Created by agm on 24/05/2017.
 */
public class JobScheduler {
    private static Scheduler scheduler = null;

    public static void init() {

        try {
            scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.start();
            initWatcherLoading();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public static Scheduler getScheduler() {
        return scheduler;
    }

    private static void initWatcherLoading() {
        Session session = Database.getSession();
        Transaction tx = null;

        try {
            List listVd = session.createCriteria(VirtualDevice.class).list();
            Set<String> set = new HashSet<String>(listVd);

            for (Object o : set) {
                VirtualDevice tmpVd = (VirtualDevice) o;

                if (tmpVd.getCron().equals(VirtualDevice.CRON.HOUR)) {
                    generateTaskHourly(tmpVd);
                } else if (tmpVd.getCron().equals(VirtualDevice.CRON.DAILY)) {
                    generateTaskDaily(tmpVd);
                } else if (tmpVd.getCron().equals(VirtualDevice.CRON.WEEKLY)) {
                    generateTaskWeekly(tmpVd);
                }
            }

        } catch (HibernateException e) {
            tx.rollback();
        }
    }
}
