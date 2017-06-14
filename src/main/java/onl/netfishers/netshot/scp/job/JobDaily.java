package onl.netfishers.netshot.scp.job;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.scp.device.ScpStepFolder;
import onl.netfishers.netshot.scp.device.VirtualDevice;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static onl.netfishers.netshot.scp.job.JobTools.generateScp;

/**
 * Created by agm on 02/06/2017.
 */
public class JobDaily implements Job {

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(JobDaily.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Session session = Database.getSession();
        Date d = new Date();

        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            long id = dataMap.getLongValue("v_id");
            VirtualDevice virtualDevice = (VirtualDevice) session.get(VirtualDevice.class, id);
            if (virtualDevice != null) {
                List<ScpStepFolder> scp = new ArrayList<>();
                scp.addAll(virtualDevice.getFile());
                long diff = 0;
                if (scp.size() == 0) {
                    diff = JobTools.getTimeDiffDays(d, virtualDevice.getHour());
                } else if (scp.size() == 1) {
                    ScpStepFolder last = scp.get(scp.size() - 1);
                    diff = JobTools.getTimeDiffDays(d, last.getCreated());
                } else if (scp.size() >= 2) {
                    ScpStepFolder last = scp.get(scp.size() - 1);
                    diff = JobTools.getTimeDiffDays(d, parseDate(last.getCreated_at()));
                }
                if (diff > 1) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(d);
                    cal.add(Calendar.DATE, -1);
                    Date newDate = cal.getTime();

                    ScpStepFolder newScp = generateScp(virtualDevice, newDate);

                    session.save(newScp);
                    tx.commit();
                }
            }
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error Crontask Daily.", e);
        } finally {
            session.close();
        }
    }


    private Date parseDate(String s) {
        try {
            SimpleDateFormat timeStamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);
            return timeStamp.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Date();
    }
}