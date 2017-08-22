package nodes;

import codex.explorer.tree.AbstractNode;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class RepositoryRoot extends AbstractNode
{

    public RepositoryRoot() {
        super(ImageUtils.getByPath("/images/repo.png"), Language.get("title"), Language.get("desc"));
    }
    
}