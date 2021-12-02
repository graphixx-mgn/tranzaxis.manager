package manager.nodes;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends Catalog {

    public DatabaseRoot() {
        super(null, ImageUtils.getByPath("/images/databases.png"), null, Language.get("desc"));
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Database.class;
    }
    
}