package manager.nodes;

import codex.model.Entity;
import codex.model.Access;
import codex.type.StringList;
import codex.utils.ImageUtils;
import java.util.ArrayList;

public class System extends Entity {
    
    public System(String title) {
        super(ImageUtils.getByPath("/images/instance.png"), title, null);
        model.addProperty("jvmServer",    new StringList(new ArrayList<>()), true, Access.Select, true);
        model.addProperty("jvmExplorer",  new StringList(new ArrayList<>()), true, Access.Select, true);
        model.addProperty("jvmDesigner",  new StringList(new ArrayList<>()), true, Access.Select, true);
    }
    
}