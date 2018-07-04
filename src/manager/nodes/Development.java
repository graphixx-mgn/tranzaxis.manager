package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Catalog;
import codex.utils.ImageUtils;

public class Development extends Catalog {
    
    public Development(INode parent) {
        super(parent, ImageUtils.getByPath("/images/development.png"), "title", null);
    }
    
    @Override
    public Class getChildClass() {
        return Offshoot.class;
    }
    
    @Override
    public boolean allowModifyChild() {
        return false;
    };
    
}
