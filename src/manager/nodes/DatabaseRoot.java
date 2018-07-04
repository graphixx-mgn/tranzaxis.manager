package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Catalog;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends Catalog {

    public DatabaseRoot(INode parent) {
        super(parent, ImageUtils.getByPath("/images/databases.png"), "title", Language.get("desc"));
    }

    @Override
    public Class getChildClass() {
        return Database.class;
    }
    
}