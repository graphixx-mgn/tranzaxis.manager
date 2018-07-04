package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Catalog;
import codex.utils.ImageUtils;

public class ReleaseList extends Catalog {

    public ReleaseList(INode parent) {
        super(parent, ImageUtils.getByPath("/images/releases.png"), "title", null);
    }

    @Override
    public Class getChildClass() {
        return Release.class;
    }
    
    @Override
    public boolean allowModifyChild() {
        return false;
    };
    
}
