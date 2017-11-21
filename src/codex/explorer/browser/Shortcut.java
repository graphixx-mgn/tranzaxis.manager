package codex.explorer.browser;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.type.Str;

public class Shortcut extends Entity {
    
    public Shortcut(String title) {
        super(null, null, null);
        
        model.addUserProp("entity", new EntityRef(null), true, null);
        model.addUserProp("command", new Str(null), true, null);
    }
    
}
