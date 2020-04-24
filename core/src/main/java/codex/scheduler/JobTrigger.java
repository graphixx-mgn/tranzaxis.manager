package codex.scheduler;

import codex.context.IContext;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.model.Access;
import codex.model.ClassCatalog;
import codex.model.PolyMorph;
import codex.task.ITaskListener;
import codex.type.AnyType;
import codex.type.EntityRef;

@LoggingSource
@IContext.Definition(id = "JSE", name = "Job Scheduler", icon = "/images/schedule.png")
@ClassCatalog.Definition(selectorProps = {Schedule.PROP_EXT_INFO})
abstract class JobTrigger extends PolyMorph implements IContext {

    final static String PROP_EXT_INFO = "extInfo";

    private final AbstractJob job;

    JobTrigger(EntityRef owner, String title) {
        super(owner, title);
        job = (AbstractJob) owner.getValue();

        // Properties
        model.addDynamicProp(PROP_EXT_INFO, new AnyType(), Access.Edit, null);

        // Handlers
        setMode(job.isDisabled() ? 0 : INode.MODE_ENABLED);
        job.model.addChangeListener((name, oldValue, newValue) -> {
            if (name.equals(Job.PROP_JOB_DISABLE)) {
                setMode(job.isDisabled() ? 0 : INode.MODE_ENABLED);
            }
        });
    }

    protected String getJobTitle() {
        return job.getPID();
    }

    protected boolean executeJob() {
        return this.executeJob(null);
    }

    protected boolean executeJob(ITaskListener listener) {
        if (!job.isDisabled()) {
            job.executeJob(listener, false);
        } else {
            Logger.getLogger().info(
                    "Job [{0}] is disabled and could not be executed",
                    getJobTitle()
            );
        }
        return !job.isDisabled();
    }

    void setExtInfo(Object info) {
        model.setValue(PROP_EXT_INFO, info);
    }
}
