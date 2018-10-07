package codex.launcher;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.type.Str;

public class Shortcut extends Entity {
    
    public Shortcut(EntityRef parent, String title) {
        super(null, null, title, null);
        try {
            model.addUserProp("section", new EntityRef(ShortcutSection.class), true, null);
            model.addUserProp("class", new Str(null), true, null);
            String entityClass = (String) model.getValue("class");
            model.addUserProp("entity", new EntityRef(
                    entityClass == null ? null : Class.forName(entityClass)
            ), true, null);
            model.addUserProp("command", new Str(null), true, null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    ShortcutSection getSection() {
        return (ShortcutSection) model.getValue("section");
    }
    
}
