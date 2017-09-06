package manager.nodes;

import codex.explorer.tree.AbstractNode;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends AbstractNode {

    public DatabaseRoot() {
        super(ImageUtils.getByPath("/images/databases.png"), Language.get("title"), Language.get("desc"));
    }
    
}