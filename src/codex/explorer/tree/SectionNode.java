package codex.explorer.tree;

import javax.swing.ImageIcon;

public abstract class SectionNode extends AbstractNode {

    public SectionNode(ImageIcon icon, String title, String hint) {
        super(icon, title, hint);
    }
    
    public abstract Class getEntityClass();
    
}
