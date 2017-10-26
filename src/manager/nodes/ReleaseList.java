package manager.nodes;

import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class ReleaseList extends Entity {
    
    public ReleaseList() {
        super(ImageUtils.getByPath("/images/releases.png"), Language.get("title"), Language.get("desc"));
    }
    
}
