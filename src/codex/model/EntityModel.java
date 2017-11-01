package codex.model;

import codex.editor.IEditor;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import codex.property.IPropertyChangeListener;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {
    
    private final List<String> persistent   = new LinkedList<>();
    private final UndoRegistry undoRegistry = new UndoRegistry();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners = new LinkedList<>();
    
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
    public final void addChangeListener(IPropertyChangeListener listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Добавление слушателя событии модели.
     */
    public final void addModelListener(IModelListener listener) {
        modelListeners.add(listener);
    }
    
    /**
     * Удаление слушателя событии модели.
     */
    public final void removeModelListener(IModelListener listener) {
        modelListeners.remove(listener);
    }

    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        if (persistent.contains(name)) {
            undoRegistry.put(name, oldValue, newValue);
        }
        changeListeners.forEach((listener) -> {
            listener.propertyChange(name, oldValue, newValue);
        });
        modelListeners.forEach((listener) -> {
            listener.modelChanged(getChanges());
        });
    }

    @Override
    public final Object getValue(String name) {
        if (undoRegistry.exists(name)) {
            return undoRegistry.previous(name);
        } else {
            return super.getValue(name);
        }
    }
    
    /**
     * Возвращает признак отсуствия несохраненных изменений среди хранимых
     * свойств модели сущности.
     */
    public final boolean hasChanges() {
        return !undoRegistry.isEmpty();
    }
    
    /**
     * Возвращает список имен модифицированных свойств.
     */
    public final List<String> getChanges() {
        return persistent.stream()
                .filter((name) -> {
                    return undoRegistry.exists(name);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Сохранение изменений модели.
     */
    public final void commit() {
        System.out.println("codex.model.EntityModel.commit(), listeners="+modelListeners.size());
        //TODO: Проверить успешность выполнения
        undoRegistry.clear();
        modelListeners.forEach((listener) -> {
            listener.modelSaved();
        });
    }
    
    /**
     * Откат изменений модели.
     */
    public final void rollback() {
        getChanges().forEach((name) -> {
            getProperty(name).setValue(undoRegistry.previous(name));
        });
        modelListeners.forEach((listener) -> {
            listener.modelRestored();
        });
    }

    @Override
    public IEditor getEditor(String name) {
        IEditor editor = super.getEditor(name);
        editor.setEditable(persistent.contains(name));
        return editor;
    }

}
