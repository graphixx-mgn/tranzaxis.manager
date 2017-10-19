package manager.nodes;

import codex.explorer.tree.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends Entity {

    public RepositoryRoot() {
        super(ImageUtils.getByPath("/images/repo.png"), Language.get("title"), Language.get("desc"));
    }
    
}