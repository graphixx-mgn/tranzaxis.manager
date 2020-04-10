package codex.scheduler;

import codex.context.IContext;
import codex.log.LoggingSource;
import codex.model.Access;
import codex.model.ClassCatalog;
import codex.model.PolyMorph;
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
    }

    protected AbstractJob getJob() {
        return job;
    }

    void setExtInfo(Object info) {
        model.setValue(PROP_EXT_INFO, info);
    }
}
