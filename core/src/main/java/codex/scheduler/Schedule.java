package codex.scheduler;

import codex.context.IContext;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.mask.DateFormat;
import codex.mask.IDateMask;
import codex.model.*;
import codex.task.ITask;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map;
import java.util.Timer;
import java.util.stream.Stream;

@LoggingSource
@IContext.Definition(id = "JSE", name = "Job Scheduler", icon = "/images/schedule.png")
public class Schedule extends Catalog implements IContext, ITaskListener {

    private final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("H:mm:ss");

    final static String PROP_KIND = "kind";
    final static String PROP_LAST = "lastTime";
    final static String PROP_NEXT = "nextTime";

    // "Timer" properties
    private final static String PROP_TIMER_AMT  = "timerAmount";
    private final static String PROP_TIMER_DIM  = "timerDimension";

    // "Daily" properties
    private final static String PROP_DAILY_TIME = "dailyTime";

    private final static Map<JobScheduler.ScheduleKind, String[]> TIMING_OPTS = new HashMap<JobScheduler.ScheduleKind, String[]>() {{
        put(JobScheduler.ScheduleKind.Undefined, new String[] {});
        put(JobScheduler.ScheduleKind.Timer,     new String[] {PROP_TIMER_AMT, PROP_TIMER_DIM});
        put(JobScheduler.ScheduleKind.Daily,     new String[] {PROP_DAILY_TIME});
    }};

    private Timer timer;
    private final AbstractJob job;

    @SuppressWarnings("unchecked")
    public Schedule(EntityRef owner, String title) {
        super(
                owner,
                ImageUtils.getByPath("/images/schedule.png"),
                title,
                "<Schedule>"
        );
        if (title == null) {
            model.getProperty(EntityModel.PID).getOwnPropValue().setValue(UUID.randomUUID().toString());
        }

        job = (AbstractJob) owner.getValue();

        // Properties
        model.addUserProp(PROP_KIND, new Enum<>(JobScheduler.ScheduleKind.Undefined, false), true, Access.Select);

        // "Timer" settings
        model.addUserProp(PROP_TIMER_AMT, new Int(null), false, Access.Select);
        model.addUserProp(PROP_TIMER_DIM, new Enum<>(Dimension.Hour), false, Access.Select);

        // "Daily" settings
        model.addUserProp(PROP_DAILY_TIME, new DateTime().setMask(DateFormat.Time.newInstance()), false, Access.Select);

        // Trigger time
        model.addUserProp(PROP_LAST, new DateTime(null), false, Access.Select);
        model.addUserProp(PROP_NEXT, new DateTime(null), false, null);

        // Property settings
        model.getEditor(PROP_LAST).setEditable(false);
        model.getEditor(PROP_NEXT).setEditable(false);
        model.addPropertyGroup(
                Language.get("group@kind"),
                TIMING_OPTS.values().stream()
                        .map(Stream::of)
                        .flatMap(x -> x)
                        .toArray(String[]::new)
        );
        adjustView();

        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            if (name.equals(PROP_KIND)) {
                adjustView();
                updateTitle();
                setNextTime(calcTime());
            } else if (Arrays.asList(TIMING_OPTS.get(getKind())).contains(name)) {
                updateTitle();
                setNextTime(calcTime());
            } else if (name.equals(PROP_NEXT)) {
                reset();
            }
        });
        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (getNextTime() != null && changes.contains(PROP_NEXT)) {
                    Logger.getLogger().debug(
                            "Init job schedule: [{0}/{1}]: {2}",
                            job.getPID(),
                            getPID(),
                            IDateMask.Format.Full.format(getNextTime())
                    );
                    schedule();
                }
            }

            @Override
            public void modelDeleted(EntityModel model) {
                Logger.getLogger().debug("Purge job schedule: [{0}/{1}]", job.getPID(), getPID());
                reset();
            }

            //TODO; Проверить изменение и откат
        });

        if (getNextTime() != null) {
            if (getNextTime().before(new Date())) {
                createTimerTask().run();
            } else {
                Logger.getLogger().debug(
                        "Init job schedule: [{0}/{1}]: {2}",
                        job.getPID(),
                        getPID(),
                        IDateMask.Format.Full.format(getNextTime())
                );
                schedule();
            }
        }
    }

    @Override
    public void setParent(INode parent) {
        updateTitle();
        super.setParent(parent);
    }

    private JobScheduler.ScheduleKind getKind() {
        return (JobScheduler.ScheduleKind) model.getUnsavedValue(PROP_KIND);
    }

    private Date getLastTime() {
        return (Date) (model.getUnsavedValue(PROP_LAST));
    }

    private void setLastTime(Date time) {
        model.setValue(PROP_LAST, time);
    }

    Date getNextTime() {
        return (Date) (model.getUnsavedValue(PROP_NEXT));
    }

    private void setNextTime(Date time) {
        model.setValue(PROP_NEXT, time);
    }

    private void adjustView() {
        setIcon(getKind().getIcon());
        JobScheduler.ScheduleKind kind = getKind();
        TIMING_OPTS.forEach((key, value) -> {
            for (String propName : value) {
                model.getEditor(propName).setVisible(key.equals(kind));
                model.getProperty(propName).setRequired(key.equals(kind));
            }
        });
    }

    private void updateTitle() {
        JobScheduler.ScheduleKind kind = getKind();
        String format = kind.getFormat();
        switch (kind) {
            case Timer:
                Integer   timerAmt = (Integer)   model.getUnsavedValue(PROP_TIMER_AMT);
                Dimension timerDim = (Dimension) model.getUnsavedValue(PROP_TIMER_DIM);
                setTitle(timerAmt == null ? Language.NOT_FOUND :
                        MessageFormat.format(
                                format,
                                Language.getPlural().npl(timerAmt, " "+timerDim.title.toLowerCase())
                        )
                );
                break;
            case Daily:
                Date time = (Date) model.getUnsavedValue(PROP_DAILY_TIME);
                setTitle(time == null ? Language.NOT_FOUND :
                        MessageFormat.format(format, TIME_FORMAT.format(time))
                );
                break;

            default:
                setTitle(format);
        }
    }

    private Date calcTime() {
        Date now  = new Date();
        Date last = getLastTime();
        Calendar calendar = Calendar.getInstance();

        switch (getKind()) {
            case Timer:
                Integer   timerAmt = (Integer)   model.getUnsavedValue(PROP_TIMER_AMT);
                Dimension timerDim = (Dimension) model.getUnsavedValue(PROP_TIMER_DIM);
                if (timerAmt == null) {
                    return null;
                } else {
                    calendar.setTime(last == null ? now : last);
                    while (calendar.getTime().compareTo(now) <= 0) {
                        System.out.println("Schedule.calcTime: add "+timerAmt+" "+timerDim);
                        calendar.add(timerDim.code, timerAmt);
                    }
                }
                break;

            case Daily:
                Date date = DateTime.trunc(last == null ? now : last);
                Date time = (Date) model.getUnsavedValue(PROP_DAILY_TIME);
                if (time == null) {
                    return null;
                } else {
                    calendar.setTime(new Date(date.getTime() + time.getTime()));
                    while (calendar.getTime().compareTo(now) <= 0) {
                        System.out.println("Schedule.calcTime: add day");
                        calendar.add(Calendar.DAY_OF_YEAR, 1);
                    }
                }
                break;
            default:
                return null;
        }
        return calendar.getTime();
    }

    private void reset() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void schedule() {
        if (timer != null) {
            timer.cancel();
        }
        if (getNextTime() != null) {
            timer = new Timer(true);
            timer.schedule(createTimerTask(), getNextTime());
        }
    }

    private TimerTask createTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                Logger.getLogger().debug("Post job ''{0}'' for execution", job.getPID());
                job.executeJob(Schedule.this, false);
            }
        };
    }

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        if (nextStatus.isFinal()) {
            task.removeListener(this);
            setLastTime(getKind() == JobScheduler.ScheduleKind.Timer ? new Date() : getNextTime());

            Date nextTime = calcTime();
            setNextTime(nextTime);
            Logger.getLogger().debug(
                    "Plan job schedule [{0}/{1}] next execution time: {2}",
                    job.getPID(),
                    getPID(),
                    IDateMask.Format.Full.format(getNextTime())
            );
            try {
                model.commit(false);
            } catch (Exception ignore) {
                //
            }
        }
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }

    enum Dimension {
        Hour(Calendar.HOUR), Minute(Calendar.MINUTE);

        private final int    code;
        private final String title;

        Dimension(int code) {
            this.code  = code;
            this.title = Language.get(Schedule.class, "dim@"+name().toLowerCase());
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
