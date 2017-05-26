package onl.netfishers.netshot.scp.job;

import org.joda.time.DateTime;
import org.joda.time.Weeks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
        SimpleDateFormat timeStamp = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss", Locale.FRANCE);
        return timeStamp.format(d);
    }

}
