package codex.scheduler.schedule;

import codex.mask.DateFormat;
import codex.model.Access;
import codex.model.ClassCatalog;
import codex.model.EntityDefinition;
import codex.scheduler.Schedule;
import codex.type.DateTime;
import codex.type.EntityRef;
import codex.utils.Language;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@ClassCatalog.Domains({Schedule.class})
@EntityDefinition(title = "class@title", icon="/images/daily.png")
public class Daily extends Schedule {

    final static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("H:mm:ss");
    private final static String PROP_RUN_TIME = "time";

    public Daily(EntityRef owner, String title) {
        super(owner, title);

        model.addUserProp(PROP_RUN_TIME, new DateTime().setMask(DateFormat.Time.newInstance()), true, Access.Select);

        model.addPropertyGroup(Language.get(Schedule.class, "group@kind"), PROP_RUN_TIME);
    }

    private Date getRunTime() {
        return (Date) model.getUnsavedValue(PROP_RUN_TIME);
    }

    @Override
    protected String calcTitle() {
        Date time = getRunTime();
        return time == null ? null : MessageFormat.format(getFormat(), TIME_FORMAT.format(time));
    }

    @Override
    protected Date calcTime() {
        Date now  = new Date();
        Date last = getLastTime();
        Calendar  calendar = Calendar.getInstance();

        Date date = DateTime.trunc(last == null ? now : last);
        Date time = getRunTime();
        if (time == null) {
            return null;
        } else {
            calendar.setTime(new Date(date.getTime() + time.getTime()));
            while (calendar.getTime().compareTo(now) <= 0) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
            return calendar.getTime();
        }
    }
}
