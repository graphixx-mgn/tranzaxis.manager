package codex.scheduler;

import codex.model.ClassCatalog;
import codex.type.EntityRef;

public abstract class Event extends JobTrigger implements ClassCatalog.IDomain {

    public Event(EntityRef owner, String title) {
        super(owner, title);
    }
}
