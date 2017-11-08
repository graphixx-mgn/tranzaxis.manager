package manager.nodes;

import codex.model.Catalog;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends Catalog {

    public RepositoryRoot() {
        super(ImageUtils.getByPath("/images/repositories.png"), Language.get("desc"));
    }

    @Override
    public Class getChildClass() {
        return Repository.class;
    }
    
}