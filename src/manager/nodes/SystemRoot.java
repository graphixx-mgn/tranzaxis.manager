package manager.nodes;

import codex.model.Entity;
import codex.model.Access;
import codex.type.StringList;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class SystemRoot extends Entity {
    
    public SystemRoot() {
        super(ImageUtils.getByPath("/images/system.png"), Language.get("title"), Language.get("desc"));
        model.addProperty("jvmServer",    new StringList("-Xmx2G"), true, Access.Select, true);
        model.addProperty("jvmExplorer",  new StringList("-Xmx1G"), true, Access.Select, true);
        model.addProperty("jvmDesigner",  new StringList("-J-Xmx4G", "-J-Xms500M"), true, Access.Select, true);
    }
    
}
