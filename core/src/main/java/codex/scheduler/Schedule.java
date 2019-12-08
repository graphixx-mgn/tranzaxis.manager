package codex.scheduler;

import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.mask.DateFormat;
import codex.mask.IDateMask;
import codex.model.*;
import codex.task.ITask;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.type.DateTime;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.util.*;
import java.util.Timer;
import java.util.function.Predicate;

@ClassCatalog.Domain()
public abstract class Schedule extends JobTrigger implements ITaskListener {

    private static final ImageIcon IMAGE_NEXT_RUN = ImageUtils.getByPath("/images/next.png");
    private static final String NOT_CONFIGURED    = Language.get("title@unknown");
    private static final String GROUP_PARAMETERS  = Language.get("group@kind");

    private final static String PROP_LAST = "lastTime";
    private final static String PROP_NEXT = "nextTime";

    private final Predicate<String> isParameterProperty = propName -> GROUP_PARAMETERS.equals(model.getPropertyGroup(propName));
    private Timer timer;

    public Schedule(EntityRef owner, String title) {
        super(owner, title);

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
                            getJob().getPID(),
                            getTitle(),
                            IDateMask.Format.Full.format(getNextTime())
                    );
                    schedule();
                }
            }
            @Override
            public void modelDeleted(EntityModel model) {
                Logger.getLogger().debug("Purge job schedule: [{0}/{1}]", getJob().getPID(), getTitle());
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
                        getJob().getPID(),
                        getTitle(),
                        IDateMask.Format.Full.format(getNextTime())
                );
                schedule();
            }
        }

        setPropertyRestriction(EntityModel.PID, Access.Any);
    }

    private Date getNextTime() {
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
        setExtInfo(new Iconified() {
           @Override
           public ImageIcon getIcon() {
               return IMAGE_NEXT_RUN;
           }

            @Override
            public String toString() {
                return DateFormat.Full.newInstance().getFormat().format(getNextTime());
            }
        });
    }

    private TimerTask createTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                Logger.getLogger().debug("Post job ''{0}'' for execution", getJob().getPID());
                getJob().executeJob(Schedule.this, false);
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
                    getJob().getPID(),
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
}
