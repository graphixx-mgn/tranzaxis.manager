package codex.model;

import codex.editor.IEditor;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import codex.property.IPropertyChangeListener;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {
    
    private final List<String> dynamicProps = new LinkedList<>();
    private final UndoRegistry undoRegistry = new UndoRegistry();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners = new LinkedList<>();

    @Override
    void addProperty(String name, IComplexType value, boolean require, Access restriction) {
        super.addProperty(name, value, require, restriction);
        getProperty(name).addChangeListener(this);
    }
    
    /**
     * Добавление хранимого свойства в сущность.
     * @param name Идентификатор свойства.
     * @param value Начальное значение свойства.
     * @param require Признак того что свойство должно иметь не значение.
     * @param restriction  Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     */
    public final void addUserProp(String name, IComplexType value, boolean require, Access restriction) {
        addProperty(name, value, require, restriction);
    }
    
    /**
     * Добавление динамического (не хранимого) свойства в сущность.
     * @param name Идентификатор свойства.
     * @param value Начальное значение свойства.
     * @param restriction Ограничение видимости свойства в редакторе и/или 
     * @param valueProvider Функция расчета значения свойства.
     * @param baseProps Список свойств сущности, при изменении которых запускать
     * функцию рассчета значения.
     */
    public final void addDynamicProp(String name, IComplexType value, Access restriction, Supplier valueProvider, String... baseProps) {
        addProperty(name, value, false, restriction);
        if (valueProvider != null) {
            addModelListener(new IModelListener() {
                @Override
                public void modelChanged(List<String> changes) {
                    List<String> intersection = new LinkedList<>(dynamicProps);
                    intersection.retainAll(Arrays.asList(baseProps));
                    if (!intersection.isEmpty()) {
                        setValue(name, valueProvider.get());
                    }
                }
                @Override
                public void modelSaved(List<String> changes) {
                    List<String> intersection = new LinkedList<>(changes);
                    intersection.retainAll(Arrays.asList(baseProps));
                    if (!intersection.isEmpty()) {
                        setValue(name, valueProvider.get());
                    }
                }
            });
        }
        dynamicProps.add(name);
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
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        if (!dynamicProps.contains(name)) {
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
        return getProperties(Access.Any).stream()
                .filter((name) -> {
                    return !dynamicProps.contains(name) && undoRegistry.exists(name);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Сохранение изменений модели.
     */
    public final void commit() {
        List<String> changes = getChanges();
        System.out.println("codex.model.EntityModel.commit(), listeners="+modelListeners.size());
        //TODO: Проверить успешность выполнения
        undoRegistry.clear();
        modelListeners.forEach((listener) -> {
            listener.modelSaved(changes);
        });
    }
    
    /**
     * Откат изменений модели.
     */
    public final void rollback() {
        List<String> changes = getChanges();
        changes.forEach((name) -> {
            getProperty(name).setValue(undoRegistry.previous(name));
        });
        modelListeners.forEach((listener) -> {
            listener.modelRestored(changes);
        });
    }

    @Override
    public IEditor getEditor(String name) {
        IEditor editor = super.getEditor(name);
        addModelListener(new IModelListener() {
            
            @Override
            public void modelSaved(List<String> changes) { 
                editor.getLabel().setText(getProperty(name).getTitle());
            }

            @Override
            public void modelChanged(List<String> changes) {
                editor.getLabel().setText(getProperty(name).getTitle() + (changes.contains(name) ? " *" : ""));
            }
            
        });
        editor.setEditable(!dynamicProps.contains(name));
        return editor;
    }

}
