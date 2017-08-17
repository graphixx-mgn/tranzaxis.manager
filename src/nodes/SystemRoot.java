package node;

import codex.explorer.tree.Node;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class SystemRoot extends Node {
    
    public SystemRoot() {
        super(ImageUtils.getByPath("/images/system.png"), Language.get("title"), Language.get("desc"));
    }
    
}
