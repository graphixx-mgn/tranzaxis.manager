package codex.scheduler.schedule;

import codex.model.Access;
import codex.model.EntityDefinition;
import codex.scheduler.Schedule;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Int;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;

@EntityDefinition(title = "class@title", icon="/images/timer.png")
public class Timer extends Schedule {

    private final static String PROP_TIMER_AMT  = "amount";
    private final static String PROP_TIMER_DIM  = "dimension";

    public Timer(EntityRef owner, String title) {
        super(owner, title);

        model.addUserProp(PROP_TIMER_AMT, new Int(null), true, Access.Select);
        model.addUserProp(PROP_TIMER_DIM, new Enum<>(Dimension.Hour), true, Access.Select);

        model.addPropertyGroup(Language.get(Schedule.class, "group@kind"), PROP_TIMER_AMT, PROP_TIMER_DIM);
    }

    private Integer getAmount() {
        return (Integer) model.getUnsavedValue(PROP_TIMER_AMT);
    }

    private Dimension getDimension() {
        return (Dimension) model.getUnsavedValue(PROP_TIMER_DIM);
    }

    @Override
    protected String calcTitle() {
        Integer   timerAmt = getAmount();
        Dimension timerDim = getDimension();
        return timerAmt == null ? null : MessageFormat.format(
                getFormat(),
                Language.getPlural().npl(timerAmt, " "+timerDim.title.toLowerCase())
        );
    }

    @Override
    protected Date calcTime() {
        Date now  = new Date();
        Date last = getLastTime();
        Calendar  calendar = Calendar.getInstance();

        Integer   timerAmt = getAmount();
        Dimension timerDim = getDimension();
        if (timerAmt == null) {
            return null;
        } else {
            calendar.setTime(last == null ? now : last);
            while (calendar.getTime().compareTo(now) <= 0) {
                calendar.add(timerDim.code, timerAmt);
            }
            return calendar.getTime();
        }
    }

    protected final void setLastTime(Date time) {
        super.setLastTime(new Date());
    }


    enum Dimension {
        Hour(Calendar.HOUR),
        Minute(Calendar.MINUTE)
        ;

        private final int    code;
        private final String title;

        Dimension(int code) {
            this.code  = code;
            this.title = Language.get(Timer.class, "dim@"+name().toLowerCase());
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
