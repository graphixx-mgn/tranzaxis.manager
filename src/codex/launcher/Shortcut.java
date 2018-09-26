package codex.launcher;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.type.Str;

public class Shortcut extends Entity {
    
    public Shortcut(EntityRef parent, String title) {
        super(null, null, title, null);
        try {
            model.addUserProp("class", new Str(null), true, null);
            if (model.getValue("class") != null) {
                Class entityClass = Class.forName((String) model.getValue("class"));
                model.addUserProp("entity", new EntityRef(entityClass), true, null);
            } else {
                model.addUserProp("entity", new EntityRef(null), true, null);
            }
            model.addUserProp("command", new Str(null), true, null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
}
