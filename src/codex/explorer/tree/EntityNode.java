package codex.explorer.tree;

import codex.model.EntityModel;
import javax.swing.ImageIcon;

public abstract class EntityNode extends AbstractNode {
    
    protected final EntityModel model;

    public EntityNode(ImageIcon icon, String title, String hint) {
        super(icon, title, hint);
        this.model = new EntityModel(title);
    }
    
}
