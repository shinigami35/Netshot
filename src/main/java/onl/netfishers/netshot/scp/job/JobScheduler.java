package onl.netfishers.netshot.scp.job;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Created by agm on 24/05/2017.
 */
public class JobScheduler {
    private static Scheduler scheduler = null;

    public static void init() {

        JobDetail jobHourly = JobBuilder.newJob(JobHourly.class)
                .withIdentity("JobHourly", "Hourly").build();
        JobDetail jobDaily = JobBuilder.newJob(JobHourly.class)
                .withIdentity("JobDaily", "Daily").build();
        JobDetail jobWeekly = JobBuilder.newJob(JobWeek.class)
                .withIdentity("JobWeekly", "Weekly").build();

        Trigger triggerHourly = TriggerBuilder
                .newTrigger()
                .withIdentity("JobHourly", "Hourly")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("* * 0/1 * * ?"))
                .build();

        Trigger triggerDaily = TriggerBuilder
                .newTrigger()
                .withIdentity("JobDaily", "Daily")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 0 0 * * ?"))
                .build();

        /*Trigger triggerWeekly = TriggerBuilder
                .newTrigger()
                .withIdentity("JobHWeekly", "Weekly")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 1 * * 1 ?"))
                .build();*/

        try {
            scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.start();
            //scheduler.scheduleJob(jobHourly, triggerHourly);
            //scheduler.scheduleJob(jobDaily, triggerDaily);
            //scheduler.scheduleJob(jobWeekly, triggerWeekly);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public static Scheduler getScheduler() {
        return scheduler;
    }
}
