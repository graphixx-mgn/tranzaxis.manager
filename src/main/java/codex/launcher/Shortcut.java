package codex.launcher;

import codex.log.Logger;
import codex.model.Entity;
import codex.type.EntityRef;
import codex.type.Int;
import codex.type.Str;

/**
 * Сущность - ярлык на команду.
 */
public class Shortcut extends Entity {
    
    public final static String PROP_SECTION      = "section";
    public final static String PROP_ENTITY_CLASS = "entityClass";
    public final static String PROP_ENTITY_ID    = "entityId";
    public final static String PROP_COMMAND      = "command";
    
    /**
     * Конструктор ярлыка.
     * @param owner Ссылка на владельца. Не используется.
     * @param title Имя ярлыка, отображаемое в окне.
     */
    public Shortcut(EntityRef owner, String title) {
        super(null, null, title, null);
        
        model.addUserProp(PROP_SECTION,      new EntityRef(ShortcutSection.class), true, null);
        model.addUserProp(PROP_ENTITY_CLASS, new Str(null), true, null);

        model.addUserProp(PROP_ENTITY_ID,    new Int(null), true, null);
        model.addUserProp(PROP_COMMAND,      new Str(null), true, null);
    }
    
    final ShortcutSection getSection() {
        return (ShortcutSection) model.getValue(PROP_SECTION);
    }
    
    final Class getEntityClass() {
        String className = (String) model.getValue(PROP_ENTITY_CLASS);
        if (className != null) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                Logger.getLogger().error("Entity class {0} not found", className);
            }
        }
        return null;
    }
    
    final Integer getEntityId() {
        return (Integer) model.getValue(PROP_ENTITY_ID);
    }
    
    final Entity getEntity() {
        EntityRef ref = EntityRef.build(
                getEntityClass(), 
                getEntityId()
        );
        return ref == null ? null : ref.getValue();
    }
    
    final String getCommand() {
        return (String) model.getValue(PROP_COMMAND);
    }
    
    final Shortcut setSection(ShortcutSection section) {
        model.setValue(PROP_SECTION, section);
        return this;
    }
    
    final Shortcut setEntity(Entity entity) {
        model.setValue(PROP_ENTITY_CLASS, entity.getClass().getCanonicalName());
        model.setValue(PROP_ENTITY_ID,    entity.getID());
        return this;
    }
    
    final Shortcut setCommand(String command) {
        model.setValue(PROP_COMMAND, command);
        return this;
    }
    
}
