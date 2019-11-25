package manager.nodes;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends Catalog {

    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        Entity.deleteInstance(entity, false, true);
    }

    public RepositoryRoot() {
        super(null, ImageUtils.getByPath("/images/repositories.png"), null, Language.get("desc"));
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Repository.class;
    }
    
}