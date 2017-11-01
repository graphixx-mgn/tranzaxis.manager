package manager.nodes;

import codex.model.Entity;
import codex.model.Access;
import codex.type.StringList;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class SystemRoot extends Entity {
    
    public SystemRoot() {
        super(ImageUtils.getByPath("/images/system.png"), Language.get("title"), Language.get("desc"));
        
        model.addUserProp("jvmServer",    new StringList("-Xmx2G"), false, Access.Select);
        model.addUserProp("jvmExplorer",  new StringList("-Xmx1G"), false, Access.Select);
        model.addUserProp("jvmDesigner",  new StringList("-J-Xmx4G", "-J-Xms500M"), false, Access.Select);
    }
    
}
