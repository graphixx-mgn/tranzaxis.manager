package manager.nodes;

import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class Development extends Entity {
    
    public Development() {
        super(ImageUtils.getByPath("/images/development.png"), "title", Language.get("desc"));
    }
    
}
