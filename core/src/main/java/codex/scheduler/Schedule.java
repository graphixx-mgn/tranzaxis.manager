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
import codex.type.Enum;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.util.*;
import java.util.Timer;
import java.util.function.Predicate;

public abstract class Schedule extends JobTrigger implements ITaskListener, ClassCatalog.IDomain {

    private static final ImageIcon IMAGE_NEXT_RUN = ImageUtils.getByPath("/images/next.png");
    private static final String NOT_CONFIGURED    = Language.get("title@unknown");
    private static final String GROUP_PARAMETERS  = Language.get("group@kind");

    private final static String PROP_LAST = "lastTime";
    private final static String PROP_NEXT = "nextTime";
    private final static String PROP_PASS = "bypass";

    private final Predicate<String> isParameterProperty = propName -> GROUP_PARAMETERS.equals(model.getPropertyGroup(propName));
    private Timer timer;

    public Schedule(EntityRef owner, String title) {
        super(owner, title);

        // Trigger time
        model.addUserProp(PROP_LAST, new DateTime(null), false, Access.Select);
        model.addUserProp(PROP_NEXT, new DateTime(null), false, null);
        model.addUserProp(PROP_PASS, new Enum<>(OverdueAction.Postpone), false, Access.Select);

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
            }
            @Override
            public void modelDeleted(EntityModel model) {
                Logger.getLogger().debug("Purge job schedule: [{0}/{1}]", getJobTitle(), getTitle());
                reset();
            }
        });

        model.addChangeListener((name, oldValue, newValue) -> {
            if (isParameterProperty.test(name)) {
                schedule();
            }
        });

        if (getNextTime() != null && getNextTime().before(new Date()) && getOverdueAction() == OverdueAction.Execute) {
            createTimerTask().run();
        } else {
            try {
                getLock().acquire();
            } catch (InterruptedException ignore) {}
            SwingUtilities.invokeLater(() -> {
                try {
                    getLock().acquire();
                    schedule();
                    getLock().release();
                } catch (InterruptedException ignore) {}
            });
        }

        setPropertyRestriction(EntityModel.PID, Access.Any);
    }

    private OverdueAction getOverdueAction() {
        return (OverdueAction) model.getValue(PROP_PASS);
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
        Date nextTime = calcTime();
        if (nextTime != null) {
            setNextTime(nextTime);
            Logger.getLogger().debug(
                    "Plan job schedule [{0}/{1}] next execution time: {2}",
                    getJobTitle(),
                    getTitle(),
                    IDateMask.Format.Full.format(nextTime)
            );
            timer = new Timer(true);
            timer.schedule(createTimerTask(), nextTime);
        }
        setExtInfo(nextTime == null ? null : new Iconified() {
           @Override
           public ImageIcon getIcon() {
               return IMAGE_NEXT_RUN;
           }

            @Override
            public String toString() {
                return DateFormat.Full.newInstance().getFormat().format(getNextTime());
            }
        });
        try {
            model.commit(false);
        } catch (Exception ignore) {}
    }

    private TimerTask createTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                Logger.getLogger().debug("Post job ''{0}'' for execution", getJobTitle());
                if (!executeJob(Schedule.this)) {
                    schedule();
                }
            }
        };
    }

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        if (nextStatus.isFinal()) {
            setLastTime(getNextTime());
            schedule();
        }
    }

    public enum OverdueAction implements Iconified {

        Execute(ImageUtils.getByPath("/images/command.png")),
        Postpone(ImageUtils.getByPath("/images/daily.png"));

        private final String    title;
        private final ImageIcon icon;

        OverdueAction(ImageIcon icon) {
            this.title = Language.get(Schedule.class, "action@"+name().toLowerCase());
            this.icon  = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }

    }
}
