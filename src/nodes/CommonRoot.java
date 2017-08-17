package node;

import codex.explorer.tree.Node;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class CommonRoot extends Node {
    
    public CommonRoot() {
        super(ImageUtils.getByPath("/images/settings.png"), Language.get("title"), Language.get("desc"));
    }
    
}