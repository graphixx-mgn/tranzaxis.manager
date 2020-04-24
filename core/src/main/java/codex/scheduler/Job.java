package codex.scheduler;

import codex.mask.IDateMask;
import codex.model.*;
import codex.type.Enum;
import codex.type.*;
import javax.swing.*;
import java.util.Date;

abstract class Job extends PolyMorph {

    @PropertyDefinition(state = true)
    private final static String PROP_JOB_STATUS    = "status";
    @PropertyDefinition(state = true)
    private final static String PROP_JOB_FINISH    = "finish";
    private final static String PROP_JOB_RESULT    = "result";
            final static String PROP_JOB_DISABLE   = "disable";

    Job(EntityRef owner, String title) {
        super(owner, title);

        // Properties
        model.addUserProp(PROP_JOB_STATUS, new Enum<>(JobScheduler.JobStatus.Undefined), false, Access.Any);
        model.addUserProp(PROP_JOB_FINISH, new DateTime(null), false, Access.Any);
        model.addUserProp(PROP_JOB_DISABLE, new Bool(false), false, Access.Select);

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

    protected boolean isDisabled() {
        return model.getUnsavedValue(PROP_JOB_DISABLE) == Boolean.TRUE;
    }

    private Date getJobFinishTime() {
        return (Date) model.getUnsavedValue(PROP_JOB_FINISH);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return JobTrigger.class;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }
}
