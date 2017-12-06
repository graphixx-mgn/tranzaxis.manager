package manager.nodes;

import codex.model.Access;
import codex.model.Entity;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import java.util.ArrayList;

public class System extends Entity {
    
    public System(String title) {
        super(ImageUtils.getByPath("/images/instance.png"), title, null);
        
        model.addUserProp("jvmServer",    new ArrStr(new ArrayList<>()), false, Access.Select);
        model.addUserProp("jvmExplorer",  new ArrStr(new ArrayList<>()), false, Access.Select);
        model.addUserProp("jvmDesigner",  new ArrStr(new ArrayList<>()), false, Access.Select);
        model.addUserProp("database",     new EntityRef(Database.class), false, null);
    }
    
}