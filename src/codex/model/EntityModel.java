package codex.model;

import codex.property.PropertyChangeListener;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements PropertyChangeListener {
    
    private final List<String> persistent   = new LinkedList<>();
    private final UndoRegistry undoRegistry = new UndoRegistry();
    private final List<PropertyChangeListener> listeners = new LinkedList<>();
    
    /**
     * Добавление свойства в сущность с возможностью указания должно ли свойство 
     * быть хранимым.
     * @param propHolder Ссылка на свойство.
     * @param restriction Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     * @param isPersistent Флаг, указывающий что свойсто - хранимое.
     */
    public void addProperty(String name, IComplexType value, boolean require, Access restriction, boolean isPersistent) {
        addProperty(name, value, require, restriction);
        if (isPersistent) {
            persistent.add(name);
        }
    }

    @Override
    public void addProperty(String name, IComplexType value, boolean require, Access restriction) {
        super.addProperty(name, value, require, restriction);
        getProperty(name).addChangeListener(this);
    }
    
    /**
     * Добавление слушателя события изменения значения свойства.
     */
    public final void addChangeListener(PropertyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        undoRegistry.put(name, oldValue, newValue);
        listeners.forEach((listener) -> {
            listener.propertyChange(name, oldValue, newValue);
        });
    }

    @Override
    public Object getValue(String name) {
        if (undoRegistry.exists(name)) {
            return undoRegistry.previous(name);
        } else {
            return super.getValue(name);
        }
    }
    
}
