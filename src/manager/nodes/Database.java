package manager.nodes;

import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class Database extends Entity {
    
    private int counter = 0;

    public Database(String title) {
        super(ImageUtils.getByPath("/images/database.png"), title, null);
        
        model.addUserProp("dbUrl", 
                new Str(null).setMask(new RegexMask("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}/\\w+", Language.get("dbUrl.error"))),
        true, Access.Select);
        model.addUserProp("dbSchema", new Str(null), true, null);
        model.addUserProp("dbPass", new Str(null), true, Access.Select);
        model.addDynamicProp("version", new Str(null), Access.Select, () -> {
            return model.getValue("dbSchema");
        },
        "dbSchema", "dbPass");
        model.addUserProp("instanceId", new Str(null), true, Access.Select);
        model.addUserProp("layerURI", new Str(null), true, Access.Select);
        model.addUserProp("userNote", new Str(null), false, null);
    }
    
}
