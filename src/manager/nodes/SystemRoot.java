package manager.nodes;

import codex.explorer.tree.Entity;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.type.StringList;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class SystemRoot extends Entity {
    
    public SystemRoot() {
        super(ImageUtils.getByPath("/images/system.png"), Language.get("title"), Language.get("desc"));
        model.addPersistProperty(new PropertyHolder("jvmServer",    new StringList("-Xmx2G"), true), Access.Select);
        model.addPersistProperty(new PropertyHolder("jvmExplorer",  new StringList("-Xmx1G"), true), Access.Select);
        model.addPersistProperty(new PropertyHolder("jvmDesigner",  new StringList("-J-Xmx4G", "-J-Xms500M"), true), Access.Select);
    }
    
}
