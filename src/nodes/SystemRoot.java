package nodes;

import codex.explorer.tree.AbstractNode;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class SystemRoot extends AbstractNode {
    
    public SystemRoot() {
        super(ImageUtils.getByPath("/images/system.png"), Language.get("title"), Language.get("desc"));
    }
    
}
