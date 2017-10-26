package manager.nodes;

import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends Entity {

    public RepositoryRoot() {
        super(ImageUtils.getByPath("/images/repositories.png"), Language.get("title"), null);
    }
    
}