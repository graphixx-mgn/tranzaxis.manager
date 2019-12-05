package codex.scheduler;

import codex.context.IContext;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.mask.IDateMask;
import codex.model.*;
import codex.task.ITask;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.type.DateTime;
import codex.type.EntityRef;
import codex.utils.Language;
import java.util.*;
import java.util.function.Predicate;

@LoggingSource
@IContext.Definition(id = "JSE", name = "Job Scheduler", icon = "/images/schedule.png")
@ClassCatalog.Definition(selectorProps = {Schedule.PROP_NEXT})
public abstract class Schedule extends PolyMorph implements IContext, ITaskListener {

    private static final String NOT_CONFIGURED   = Language.get("title@unknown");
    private static final String GROUP_PARAMETERS = Language.get("group@kind");

    final static String PROP_LAST = "lastTime";
    final static String PROP_NEXT = "nextTime";

    private final Predicate<String> isParameterProperty = propName -> GROUP_PARAMETERS.equals(model.getPropertyGroup(propName));
    private Timer timer;
    private final AbstractJob job;

    public Schedule(EntityRef owner, String title) {
        super(owner, title);
        job = (AbstractJob) owner.getValue();

        // Trigger time
        model.addUserProp(PROP_LAST, new DateTime(null), false, Access.Select);
        model.addUserProp(PROP_NEXT, new DateTime(null), false, null);

        // Property settings
        model.getEditor(PROP_LAST).setEditable(false);
        model.getEditor(PROP_NEXT).setEditable(false);

        if (title == null) {
            //noinspection unchecked
            model.getProperty(EntityModel.PID).getOwnPropValue().setValue(UUID.randomUUID().toString());
        }
        // Handlers
        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.stream().anyMatch(isParameterProperty)) {
                    updateTitle();
                }
                if (getNextTime() != null && changes.contains(PROP_NEXT)) {
                    Logger.getLogger().debug(
                            "Init job schedule: [{0}/{1}]: {2}",
                            job.getPID(),
                            getTitle(),
                            IDateMask.Format.Full.format(getNextTime())
                    );
                    schedule();
                }
            }
            @Override
            public void modelDeleted(EntityModel model) {
                Logger.getLogger().debug("Purge job schedule: [{0}/{1}]", job.getPID(), getTitle());
                reset();
            }
        });
        model.addChangeListener((name, oldValue, newValue) -> {
            if (isParameterProperty.test(name)) {
                setNextTime(calcTime());
            } else if (name.equals(PROP_NEXT)) {
                reset();
            }
        });

        if (getNextTime() != null) {
            if (getNextTime().before(new Date())) {
                //TODO: Если сработали несколько триггеров - нужно перепланировать
                createTimerTask().run();
            } else {
                Logger.getLogger().debug(
                        "Init job schedule: [{0}/{1}]: {2}",
                        job.getPID(),
                        getTitle(),
                        IDateMask.Format.Full.format(getNextTime())
                );
                schedule();
            }
        }

        setPropertyRestriction(EntityModel.PID, Access.Any);
    }

    protected final Date getNextTime() {
        return (Date) (model.getUnsavedValue(PROP_NEXT));
    }

    private void setNextTime(Date time) {
        model.setValue(PROP_NEXT, time);
    }

    protected final Date getLastTime() {
        return (Date) (model.getUnsavedValue(PROP_LAST));
    }

    protected void setLastTime(Date time) {
        model.setValue(PROP_LAST, time);
    }

    @Override
    public final void setParent(INode parent) {
        if (parent != null) {
            updateTitle();
        }
        super.setParent(parent);
    }

    protected final String getFormat() {
        return Language.get(getClass(), "title@format");
    }

    protected abstract String calcTitle();
    protected abstract Date   calcTime();

    private void updateTitle() {
        String title = calcTitle();
        setTitle(title == null ? NOT_CONFIGURED : title);
    }

    private void reset() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void schedule() {
        reset();
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
            setLastTime(getNextTime());

            Date nextTime = calcTime();
            setNextTime(nextTime);
            Logger.getLogger().debug(
                    "Plan job schedule [{0}/{1}] next execution time: {2}",
                    job.getPID(),
                    getTitle(),
                    IDateMask.Format.Full.format(getNextTime())
            );
            try {
                model.commit(false);
            } catch (Exception ignore) {
                //
            }
        }
    }

//    private final static int FIRST_WEEK_DAY = Calendar.getInstance().getFirstDayOfWeek();
//    private final static String[] DAY_NAMES = DateFormatSymbols.getInstance().getShortWeekdays();
//    private final static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("H:mm:ss");

//    @SuppressWarnings("unchecked")
//    public Schedule(EntityRef owner, String title) {
//
//
//        // Handlers
//        model.addChangeListener((name, oldValue, newValue) -> {
//            if (name.equals(PROP_KIND)) {
//                adjustView();
//                updateTitle();
//                setNextTime(calcTime());
//            } else if (Arrays.asList(TIMING_OPTS.get(getKind())).contains(name)) {
//                updateTitle();
//                setNextTime(calcTime());
//            } else if (name.equals(PROP_NEXT)) {
//                reset();
//            }
//        });
//        model.addModelListener(new IModelListener() {
//            @Override
//            public void modelSaved(EntityModel model, List<String> changes) {
//                if (getNextTime() != null && changes.contains(PROP_NEXT)) {
//                    Logger.getLogger().debug(
//                            "Init job schedule: [{0}/{1}]: {2}",
//                            job.getPID(),
//                            getPID(),
//                            IDateMask.Format.Full.format(getNextTime())
//                    );
//                    schedule();
//                }
//            }
//
//            @Override
//            public void modelDeleted(EntityModel model) {
//                Logger.getLogger().debug("Purge job schedule: [{0}/{1}]", job.getPID(), getPID());
//                reset();
//            }
//        });
//    }

//    @SuppressWarnings("unchecked")
//    private void updateTitle() {
//        JobScheduler.ScheduleKind kind = getKind();
//        String format = kind.getFormat();
//        switch (kind) {
//            case Weekly: {
//                    Map<Integer, Boolean> weekDays = (Map<Integer, Boolean>) model.getUnsavedValue(PROP_WEEK_DAYS);
//                    String days = weekDays.entrySet().stream().filter(Map.Entry::getValue).map(entry -> DAY_NAMES[entry.getKey()]).collect(Collectors.joining(","));
//                    Date time = (Date) model.getUnsavedValue(PROP_DAY_TIME);
//                    if (days.isEmpty()) days = "?";
//                    setTitle(time == null ? Language.NOT_FOUND :
//                            MessageFormat.format(format, days, TIME_FORMAT.format(time))
//                    );
//                }
//                break;
//
//            default:
//                setTitle(format);
//        }
//    }

//    private Date calcTime() {
//        Date now  = new Date();
//        Date last = getLastTime();
//        Calendar calendar = Calendar.getInstance();
//
//        switch (getKind()) {
//            case Daily:
//                Date date = DateTime.trunc(last == null ? now : last);
//                Date time = (Date) model.getUnsavedValue(PROP_DAY_TIME);
//                if (time == null) {
//                    return null;
//                } else {
//                    calendar.setTime(new Date(date.getTime() + time.getTime()));
//                    while (calendar.getTime().compareTo(now) <= 0) {
//                        calendar.add(Calendar.DAY_OF_YEAR, 1);
//                    }
//                }
//                break;
//
//            case Weekly:
//
//                break;
//
//            default:
//                return null;
//        }
//        return calendar.getTime();
//    }


//    private static class WeekEditor extends AbstractEditor<codex.type.Map<Integer, Boolean>, Map<Integer, Boolean>> {
//
//        private Map<Integer, ToggleButton> buttonSet;
//
//        WeekEditor(PropertyHolder<codex.type.Map<Integer, Boolean>, Map<Integer, Boolean>> propHolder) {
//            super(propHolder);
//        }
//
//        @Override
//        public Box createEditor() {
//            buttonSet = createButtons();
//
//            JPanel wrapper = new JPanel();
//            wrapper.setLayout(new GridLayout(1, 7, 1, 0));
//
//            Box box = new Box(BoxLayout.LINE_AXIS) {
//                { add(wrapper); }
//
//                @Override
//                public java.awt.Dimension getPreferredSize() {
//                    return wrapper.getPreferredSize();
//                }
//            };
//            buttonSet.forEach((dayIdx, button) -> wrapper.add(button));
//            return box;
//        }
//
//        Map<Integer, ToggleButton> createButtons() {
//            Map<Integer, ToggleButton> buttonMap = new LinkedHashMap<>();
//
//            for (int i=Schedule.FIRST_WEEK_DAY-1; i<FIRST_WEEK_DAY+6; i++) {
//                int dayIdx = i % 7 + 1;
//                String dayName = DAY_NAMES[dayIdx];
//                boolean isWeekend = dayIdx == 1 || dayIdx == 7;
//
//                ToggleButton button = new ToggleButton(
//                        ImageUtils.createBadge(dayName, isWeekend ? Color.decode("#DE5347") : Color.decode("#3399FF"), Color.WHITE),
//                        null, false
//                ) {{
//                    setLayout(new BorderLayout(0, 0));
//                    button.getParent().add(button, BorderLayout.CENTER);
//                }};
//
//                buttonMap.put(dayIdx, button);
//                button.addActionListener(e -> {
//                    Map<Integer, Boolean> values = propHolder.getPropValue().getValue();
//                    if (values.get(dayIdx) != button.isChecked()) {
//                        values.put(dayIdx, button.isChecked());
//                        propHolder.setValue(values);
//                    }
//                });
//            }
//            return buttonMap;
//        }
//
//        @Override
//        public void setValue(Map<Integer, Boolean> value) {
//            value.forEach((dayIdx, enabled) -> buttonSet.get(dayIdx).setChecked(enabled));
//        }
//    }
}
