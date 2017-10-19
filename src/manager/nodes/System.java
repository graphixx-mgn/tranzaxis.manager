package manager.nodes;

import codex.explorer.tree.Entity;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.type.Int;
import codex.type.Str;
import codex.type.StringList;
import codex.utils.ImageUtils;
import java.util.ArrayList;

public class System extends Entity {
    
    public System() {
        super(ImageUtils.getByPath("/images/instance.png"), "Wirecard Trunk", null);
        model.addPersistProperty(new PropertyHolder("jvmServer",    new StringList(new ArrayList<>()), true), Access.Select);
        model.addPersistProperty(new PropertyHolder("jvmExplorer",  new StringList(new ArrayList<>()), true), Access.Select);
        model.addPersistProperty(new PropertyHolder("jvmDesigner",  new StringList(new ArrayList<>()), true), Access.Select);
        
        model.addPersistProperty(new PropertyHolder("st",  new Str("TEST"), true), Access.Select);
        model.addPersistProperty(new PropertyHolder("st1",  new Int(11324), true), Access.Select);
    }
    
}