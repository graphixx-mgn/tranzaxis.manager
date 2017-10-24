package manager.nodes;

import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class DatabaseRoot extends Entity {

    public DatabaseRoot() {
        super(ImageUtils.getByPath("/images/databases.png"), Language.get("title"), Language.get("desc"));
    }
    
}