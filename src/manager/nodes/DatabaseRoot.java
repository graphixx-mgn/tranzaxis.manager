package manager.nodes;

import codex.model.Catalog;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends Catalog {

    public DatabaseRoot() {
        super(null, ImageUtils.getByPath("/images/databases.png"), "title", Language.get("desc"));
    }

    @Override
    public Class getChildClass() {
        return Database.class;
    }
    
}