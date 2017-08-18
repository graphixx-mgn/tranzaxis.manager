package nodes;

import codex.explorer.tree.Node;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends Node {

    public DatabaseRoot() {
        super(ImageUtils.getByPath("/images/database.png"), Language.get("title"), Language.get("desc"));
    }
    
}