package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Access;
import codex.model.Catalog;
import codex.type.ArrStr;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class EnvironmentRoot extends Catalog {
    
    public EnvironmentRoot(INode parent) {
        super(parent, ImageUtils.getByPath("/images/system.png"), "title", Language.get("desc"));
        
        model.addUserProp("jvmServer",    new ArrStr("-Xmx2G"), false, Access.Select);
        model.addUserProp("jvmExplorer",  new ArrStr("-Xmx1G"), false, Access.Select);
        model.addUserProp("jvmDesigner",  new ArrStr("-J-Xmx4G", "-J-Xms500M"), false, Access.Select);
    }

    @Override
    public Class getChildClass() {
        return Environment.class;
    }
    
}
