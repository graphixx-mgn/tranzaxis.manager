package manager.nodes;

import codex.model.Catalog;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends Catalog {

    public RepositoryRoot(EntityRef parent) {
        super(parent, ImageUtils.getByPath("/images/repositories.png"), "title", Language.get("desc"));
    }

    @Override
    public Class getChildClass() {
        return Repository.class;
    }
    
}