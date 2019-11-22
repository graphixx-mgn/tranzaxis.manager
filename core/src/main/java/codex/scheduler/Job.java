package codex.scheduler;

import codex.explorer.tree.INode;
import codex.mask.IDateMask;
import codex.model.*;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@ClassCatalog.Definition(selectorProps = {Job.PROP_NEXT_SCHEDULE})
class Job extends PolyMorph {

    private static final Iconified EMPTY = new Iconified() {
        @Override
        public ImageIcon getIcon() {
            return ImageUtils.getByPath("/images/unavailable.png");
        }

        @Override
        public String toString() {
            return Language.get(Job.class, "next.empty");
        }
    };

    @PropertyDefinition(state = true)
    final static String PROP_JOB_STATUS    = "status";
    @PropertyDefinition(state = true)
    final static String PROP_JOB_FINISH    = "finish";
    final static String PROP_JOB_RESULT    = "result";
    final static String PROP_NEXT_SCHEDULE = "next";

    private IModelListener scheduleListener = new IModelListener() {
        @Override
        public void modelSaved(EntityModel model, List<String> changes) {
            if (changes.contains(Schedule.PROP_NEXT)) {
                updateNextSchedule();
            }
        }
    };

    Job(EntityRef owner, String title) {
        super(owner, title);

        // Properties
        model.addDynamicProp(PROP_NEXT_SCHEDULE, new AnyType(), Access.Edit, null);
        model.addUserProp(PROP_JOB_STATUS, new Enum<>(JobScheduler.JobStatus.Undefined), false, Access.Any);
        model.addUserProp(PROP_JOB_FINISH, new DateTime(null), false, Access.Any);

        model.addDynamicProp(PROP_JOB_RESULT, new AnyType(), Access.Extra, () -> {
            return getJobStatus() == JobScheduler.JobStatus.Undefined ? null : new Iconified() {
                @Override
                public ImageIcon getIcon() {
                    return getJobStatus().getIcon();
                }

                @Override
                public String toString() {
                    return IDateMask.Format.Full.format(getJobFinishTime());
                }
            };
        }, PROP_JOB_STATUS, PROP_JOB_FINISH);
        updateNextSchedule();
    }

    void setJobStatus(JobScheduler.JobStatus status) {
        model.setValue(PROP_JOB_STATUS, status);
        if (status != JobScheduler.JobStatus.Undefined) {
            model.setValue(PROP_JOB_FINISH, new Date());
        }
        try {
            model.commit(false, PROP_JOB_STATUS, PROP_JOB_FINISH);
        } catch (Exception ignore) {
            //
        }
    }

    private JobScheduler.JobStatus getJobStatus() {
        return (JobScheduler.JobStatus) model.getUnsavedValue(PROP_JOB_STATUS);
    }

    private Date getJobFinishTime() {
        return (Date) model.getUnsavedValue(PROP_JOB_FINISH);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Schedule.class;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public void insert(INode child) {
        super.insert(child);
        ((Entity) child).model.addModelListener(scheduleListener);
        updateNextSchedule();
    }

    @Override
    public void delete(INode child) {
        super.delete(child);
        ((Entity) child).model.removeModelListener(scheduleListener);
        updateNextSchedule();
    }

    private void updateNextSchedule() {
        Schedule nextSchedule = childrenList().stream()
                .map(iNode -> (Schedule) iNode)
                .filter(schedule -> schedule.getNextTime() != null)
                .min(Comparator.comparing(Schedule::getNextTime))
                .orElse(null);
        model.setValue(PROP_NEXT_SCHEDULE, nextSchedule == null ? EMPTY : new ScheduleProxy(nextSchedule));
    }


    private static class ScheduleProxy implements Iconified {
        private final Schedule schedule;

        ScheduleProxy(Schedule schedule) {
            this.schedule = schedule;
        }

        @Override
        public ImageIcon getIcon() {
            return schedule.getIcon();
        }

        @Override
        public String toString() {
            return MessageFormat.format(
                    "{0} ({1})",
                    schedule.getTitle(),
                    IDateMask.Format.Full.format(schedule.getNextTime())
            );
        }
    }

}
