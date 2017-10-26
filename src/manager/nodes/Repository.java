package manager.nodes;

import codex.model.Entity;
import codex.type.Str;
import codex.utils.ImageUtils;

public class Repository extends Entity {
    
    public Repository(String title) {
        super(ImageUtils.getByPath("/images/repository.png"), title, null);
        
        model.addProperty("repoUrl", new Str(null), false, null, false);
    }
    
}
