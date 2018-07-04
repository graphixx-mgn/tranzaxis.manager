package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Catalog;
import javax.swing.ImageIcon;

public abstract class BinarySource extends Catalog {

    public BinarySource(INode parent, ImageIcon icon, String title) {
        super(parent, icon, title, null);
    }

    @Override
    public Class getChildClass() {
        return null;
    }
    
}
