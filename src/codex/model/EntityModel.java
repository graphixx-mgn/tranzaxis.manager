package codex.model;

import codex.property.PropertyHolder;
import java.util.LinkedList;
import java.util.List;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel {
    
    private final List<String> persistent = new LinkedList<>();
    
    /**
     * Добавление свойства в сущность с возможностью указания должно ли свойство 
     * быть хранимым.
     * @param propHolder Ссылка на свойство.
     * @param restriction Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     * @param isPersistent Флаг, указывающий что свойсто - хранимое.
     */
    public void addProperty(PropertyHolder propHolder, Access restriction, boolean isPersistent) {
        addProperty(propHolder, restriction);
        if (isPersistent) {
            persistent.add(propHolder.getName());
        }
    }
    
}
