package onl.netfishers.netshot.scp.job;

import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.scp.device.ScpStepFolder;
import onl.netfishers.netshot.scp.device.VirtualDevice;
import org.joda.time.DateTime;
import org.joda.time.Weeks;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.quartz.CalendarIntervalScheduleBuilder.calendarIntervalSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * Created by agm on 23/05/2017.
 */
public class JobTools {

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(JobTools.class);


    public static long getTimeDiffHours(Date dateOne, Date dateTwo) {
        long timeDiff = Math.abs(dateOne.getTime() - dateTwo.getTime());
        return TimeUnit.MILLISECONDS.toHours(timeDiff);
    }

    public static long getTimeDiffDays(Date dateOne, Date dateTwo) {
        String diff = "";
        long timeDiff = Math.abs(dateOne.getTime() - dateTwo.getTime());
        //diff = String.format("%d hour(s) %d min(s)", TimeUnit.MILLISECONDS.toHours(timeDiff), TimeUnit.MILLISECONDS.toMinutes(timeDiff) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeDiff)));
        return TimeUnit.MILLISECONDS.toDays(timeDiff);
    }

    public static long getTimeDiffWeek(Date dateOne, Date dateTwo) {
        DateTime dateTime1 = new DateTime(dateOne);
        DateTime dateTime2 = new DateTime(dateTwo);
        return Weeks.weeksBetween(dateTime1, dateTime2).getWeeks();
    }

    public static String convertDate(Date d) {
        SimpleDateFormat timeStamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);
        return timeStamp.format(d);
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static Path generatePathDest(String folder) {
        String firstPath = Netshot.getConfig("netshot.watch.folderListen");
        return generatePath(firstPath, folder);
    }

    public static Path generatePathMove(String folder) {
        String firstPath = Netshot.getConfig("netshot.watch.moveFile");
        return generatePath(firstPath, folder);
    }

    public static Path generatePath(String firstPath, String folder) {
        String tmpPath;
        if (firstPath.charAt(firstPath.length() - 1) == '/')
            tmpPath = firstPath + folder;
        else
            tmpPath = firstPath + '/' + folder;
        return Paths.get(tmpPath);
    }

    public static ScpStepFolder generateScp(VirtualDevice virtualDevice, Date newDate) {
        ScpStepFolder newScp = new ScpStepFolder();
        newScp.setNameFile("");
        newScp.setSize(0);
        newScp.setCreated_at(JobTools.convertDate(newDate));
        newScp.setVirtual(virtualDevice);
        newScp.setStatus(ScpStepFolder.TaskStatus.FAILED);
        newScp.setCreated(newDate);
        return newScp;
    }

    public static void generateTaskHourly(VirtualDevice vs) {

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(vs.getHour());
        cal.add(java.util.Calendar.MINUTE, 30);
        Date newDate = cal.getTime();


        JobDetail jobHourly = JobBuilder.newJob(JobHourly.class)
                .withIdentity("JobHourly_" + vs.getId(), "Hourly_" + vs.getId()).build();

        JobDataMap jobDataMap = jobHourly.getJobDataMap();
        jobDataMap.put("v_id", vs.getId());

        Trigger triggerHourly = TriggerBuilder
                .newTrigger()
                .withIdentity("JobHourly_" + vs.getId(), "Hourly_" + vs.getId())
                .startAt(newDate)
                .withSchedule(simpleSchedule()
                        .withIntervalInHours(1)
                        .repeatForever())
                .build();

        Scheduler scheduler = JobScheduler.getScheduler();
        try {
            scheduler.scheduleJob(jobHourly, triggerHourly);
        } catch (SchedulerException e) {
            logger.error("Error Crontask Hourly.", e);
        }
    }

    public static void generateTaskDaily(VirtualDevice vs) {

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(vs.getHour());
        cal.set(java.util.Calendar.HOUR_OF_DAY, 1);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.add(Calendar.DATE, 1);
        Date newDate = cal.getTime();

        JobDetail jobDaily = JobBuilder.newJob(JobHourly.class)
                .withIdentity("JobDaily_" + vs.getId(), "Daily_" + vs.getId()).build();

        JobDataMap jobDataMap = jobDaily.getJobDataMap();
        jobDataMap.put("v_id", vs.getId());

        Trigger triggerDaily = TriggerBuilder
                .newTrigger()
                .withIdentity("JobDaily_" + vs.getId(), "Daily_" + vs.getId())
                .startAt(newDate)
                .withSchedule(simpleSchedule()
                        .withIntervalInHours(24)
                        .repeatForever())
                .build();

        Scheduler scheduler = JobScheduler.getScheduler();
        try {
            scheduler.scheduleJob(jobDaily, triggerDaily);
        } catch (SchedulerException e) {
            logger.error("Error Crontask Daily.", e);
        }
    }

    public static void generateTaskWeekly(VirtualDevice vs) {

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(vs.getHour());
        cal.set(java.util.Calendar.HOUR_OF_DAY, 1);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.add(Calendar.DATE, 1);
        cal.add(Calendar.WEEK_OF_YEAR, 1);
        Date newDate = cal.getTime();

        JobDetail jobWeekly = JobBuilder.newJob(JobHourly.class)
                .withIdentity("JobWeekly_" + vs.getId(), "Weekly_" + vs.getId()).build();

        JobDataMap jobDataMap = jobWeekly.getJobDataMap();
        jobDataMap.put("c", vs.getId());

        Trigger triggerWeekly = TriggerBuilder
                .newTrigger()
                .withIdentity("JobWeekly_" + vs.getId(), "Weekly_" + vs.getId())
                .startAt(newDate)
                .withSchedule(calendarIntervalSchedule()
                        .withIntervalInWeeks(1))
                .build();

        Scheduler scheduler = JobScheduler.getScheduler();
        try {
            scheduler.scheduleJob(jobWeekly, triggerWeekly);
        } catch (SchedulerException e) {
            logger.error("Error Crontask Daily.", e);
        }
    }

    public static String generateDateSave(Date d) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy_HH-mm");
        return simpleDateFormat.format(d);
    }

}
