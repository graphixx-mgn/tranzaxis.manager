package codex.scheduler;

import codex.model.Catalog;
import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class JobCatalog extends Catalog {

    JobCatalog() {
        this(null, Language.get(JobScheduler.class, "catalog@title"));
    }

    private JobCatalog(EntityRef owner, String title) {
        super(
                null,
                ImageUtils.getByPath("/images/jobs.png"),
                title,
                null
        );
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Job.class;
    }

    @Override
    public boolean allowModifyChild() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }

}
