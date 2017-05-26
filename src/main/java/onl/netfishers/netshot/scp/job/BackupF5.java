package onl.netfishers.netshot.scp.job;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.scp.VirtualDevice;
import onl.netfishers.netshot.scp.jsch.Jsch;
import org.hibernate.Session;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by agm on 26/05/2017.
 */
public class BackupF5 implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Object id = dataMap.get("id");
        Session session = Database.getSession();
        VirtualDevice vs = (VirtualDevice) session.get(VirtualDevice.class, (long) id);
        if (vs != null) {
            SimpleDateFormat df = new SimpleDateFormat("mm-dd-YYYY");
            String name = "Netshot_Backup_" + vs.getName().replaceAll("\\s+", "") + "_" + df.format(new Date()) + ".ucs";
            if (vs.getLogin() != null && vs.getPassword() != null) {
                Jsch j = new Jsch(vs.getLogin(), vs.getPassword(), vs.getIp(), "");
                j.connect();
                j.sendCommand("save /sys ucs /tmp/" + name);
            }
        }
    }
}
