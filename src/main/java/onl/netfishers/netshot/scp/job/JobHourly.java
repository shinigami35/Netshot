package onl.netfishers.netshot.scp.job;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.scp.ScpStepFolder;
import onl.netfishers.netshot.scp.TaskScp;
import onl.netfishers.netshot.scp.VirtualDevice;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by agm on 23/05/2017.
 */
public class JobHourly implements Job {

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(JobHourly.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Session session = Database.getSession();
        String request = "FROM VirtualDevice vd WHERE vd.type = :type";
        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            List l = session.createQuery(request)
                    .setParameter("type", VirtualDevice.CRON.HOUR)
                    .list();
            if (l.size() > 0) {
                for (Object o : l) {
                    VirtualDevice vd = (VirtualDevice) o;
                    List<ScpStepFolder> scp = new ArrayList<>();
                    scp.addAll(vd.getFile());
                    if (scp.size() >= 2) {
                        TaskScp last = scp.get(scp.size() - 1).getTask();
                        TaskScp lastMinusOne = scp.get(scp.size() - 2).getTask();
                        long diff = JobTools.getTimeDiffHours(last.getDate(), lastMinusOne.getDate());
                        if (diff > 1) {
                            for (int i = (int) diff; i > 0; i--) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(last.getDate());
                                cal.add(Calendar.HOUR, -i);
                                Date newDate = cal.getTime();

                                ScpStepFolder newScp = new ScpStepFolder();
                                newScp.setNameFile("");
                                newScp.setSize(0);
                                newScp.setCreated_at(JobTools.convertDate(newDate));
                                newScp.setVirtual(vd);
                                session.save(newScp);

                                TaskScp newTask = new TaskScp();
                                newTask.setDate(newDate);
                                newTask.setStatus(TaskScp.TaskStatus.FAILED);
                                newTask.setScpStepFolder(newScp);
                                session.save(newTask);
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
}
