package manager.job;

import codex.model.ClassCatalog;
import codex.scheduler.AbstractJob;
import codex.type.EntityRef;

@ClassCatalog.Domain()
abstract class DevelopmentJob extends AbstractJob {

    DevelopmentJob(EntityRef owner, String title) {
        super(owner, title);
    }
}
