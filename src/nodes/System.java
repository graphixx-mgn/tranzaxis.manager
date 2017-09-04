package nodes;

import codex.explorer.tree.AbstractNode;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;

public class System extends AbstractNode {
    
    public System() {
        super(ImageUtils.getByPath("/images/instance.png"), "Wirecard Trunk", null);
        model.addPersistProperty(new PropertyHolder(String.class,  "memServer",    null, true), Access.Select);
        model.addPersistProperty(new PropertyHolder(String.class,  "memExplorer",  null, true), Access.Select);
        model.addPersistProperty(new PropertyHolder(String.class,  "memDesigner",  null, true), Access.Select);
    }
    
}
