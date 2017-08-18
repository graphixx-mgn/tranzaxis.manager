package nodes;

import codex.explorer.tree.Node;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends Node {

    public RepositoryRoot() {
        super(ImageUtils.getByPath("/images/repo.png"), Language.get("title"), Language.get("desc"));
    }
    
}