package onl.netfishers.netshot.scp.job;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.scp.ScpStepFolder;
import onl.netfishers.netshot.scp.VirtualDevice;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by agm on 23/05/2017.
 */
public class JobWeek implements Job {

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(JobWeek.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Session session = Database.getSession();
        String request = "FROM VirtualDevice vd WHERE vd.type = :type";
        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            List l = session.createQuery(request)
                    .setParameter("type", VirtualDevice.CRON.WEEKLY)
                    .list();
            if (l.size() > 0) {
                for (Object o : l) {
                    VirtualDevice vd = (VirtualDevice) o;
                    List<ScpStepFolder> scp = new ArrayList<>();
                    scp.addAll(vd.getFile());
                    if (scp.size() >= 2) {
                        ScpStepFolder last = scp.get(scp.size() - 1);
                        ScpStepFolder lastMinusOne = scp.get(scp.size() - 2);
                        long diff = JobTools.getTimeDiffWeek(parseDate(last.getCreated_at()), parseDate(lastMinusOne.getCreated_at()));
                        if (diff > 1) {
                            for (int i = (int) diff; i > 0; i--) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(parseDate(last.getCreated_at()));
                                cal.add(Calendar.WEEK_OF_YEAR, -i);
                                Date newDate = cal.getTime();

                                ScpStepFolder newScp = new ScpStepFolder();
                                newScp.setNameFile("");
                                newScp.setSize(0);
                                newScp.setCreated_at(JobTools.convertDate(newDate));
                                newScp.setVirtual(vd);
                                newScp.setStatus(ScpStepFolder.TaskStatus.FAILED);

                                session.save(newScp);
                                tx.commit();
                            }
                        }
                    }
                }
            }
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error Crontask Hourly.", e);
        } finally {
            session.close();
        }
    }

    private Date parseDate(String s) {
        try {
            SimpleDateFormat timeStamp = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss", Locale.FRANCE);
            return timeStamp.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Date();
    }
}
