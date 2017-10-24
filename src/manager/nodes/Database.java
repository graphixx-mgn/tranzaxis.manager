package manager.nodes;

import codex.model.Access;
import codex.model.Entity;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class Database extends Entity {
    
    public Database() {
        super(ImageUtils.getByPath("/images/database.png"), Language.get("title"), Language.get("desc"));
        
        model.addProperty("dbUri", new Str(null), true, Access.Select, true);
        model.addProperty("dbSchema", new Str(null), true, null, true);
        model.addProperty("dbPass", new Str(null), true, Access.Select, true);
        model.addProperty("instanceId", new Str(null), true, Access.Select, true);
        model.addProperty("layerURI", new Str(null), true, Access.Select, true);
        model.addProperty("version", new Str(null), true, null, true);
        model.addProperty("userNote", new Str(null), false, null, true);
    }
    
}
