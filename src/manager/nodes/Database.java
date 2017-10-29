package manager.nodes;

import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class Database extends Entity {
    
    public Database(String title) {
        super(ImageUtils.getByPath("/images/database.png"), title, null);
        
        model.addProperty("dbUrl", 
                new Str(null).setMask(new RegexMask("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}/\\w+", Language.get("dbUrl@error"))),
        true, Access.Select, true);
        model.addProperty("dbSchema", new Str(null), true, null, true);
        model.addProperty("dbPass", new Str(null), true, Access.Select, true);
        model.addProperty("instanceId", new Str(null), true, Access.Select, true);
        model.addProperty("layerURI", new Str(null), true, Access.Select, true);
        model.addProperty("version", new Str(null), true, null, true);
        model.addProperty("userNote", new Str(null), false, null, true);
    }
    
}
