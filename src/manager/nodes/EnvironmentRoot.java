package manager.nodes;

import codex.model.Access;
import codex.model.Catalog;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class EnvironmentRoot extends Catalog {
    
    public EnvironmentRoot(EntityRef parent) {
        super(parent, ImageUtils.getByPath("/images/system.png"), "title", Language.get("desc"));
        
        // Properties
        model.addUserProp("jvmServer",    new ArrStr("-Xmx2G"), false, Access.Select);
        model.addUserProp("jvmExplorer",  new ArrStr("-Xmx1G"), false, Access.Select);
    }

    @Override
    public Class getChildClass() {
        return Environment.class;
    }
    
}
