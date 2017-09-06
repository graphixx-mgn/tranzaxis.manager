package manager.nodes;

import codex.explorer.tree.AbstractNode;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class SystemRoot extends AbstractNode {
    
    public SystemRoot() {
        super(ImageUtils.getByPath("/images/system.png"), Language.get("title"), Language.get("desc"));
        model.addPersistProperty(new PropertyHolder(String.class,  "memServer",    "-Xmx2G -XX:MaxPermSize=500M", true), Access.Select);
        model.addPersistProperty(new PropertyHolder(String.class,  "memExplorer",  "-Xmx1G -XX:MaxPermSize=500M", true), Access.Select);
        model.addPersistProperty(new PropertyHolder(String.class,  "memDesigner",  "-J-Xmx4G -J-Xms500M", true), Access.Select);
    }
    
}
