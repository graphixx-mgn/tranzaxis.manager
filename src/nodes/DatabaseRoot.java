package nodes;

import codex.explorer.tree.AbstractNode;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends AbstractNode {

    public DatabaseRoot() {
        super(ImageUtils.getByPath("/images/database.png"), Language.get("title"), Language.get("desc"));
        model.addPersistProperty(new PropertyHolder(String.class,  "memServer",  null, true), Access.Select);
        model.addPersistProperty(new PropertyHolder(String.class,  "memExplorer",  null, true), Access.Select);
        model.addPersistProperty(new PropertyHolder(String.class,  "memDesigner",  null, true), Access.Select);
    }
    
}