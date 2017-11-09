package codex.model;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.IEditor;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import codex.property.IPropertyChangeListener;
import codex.service.ServiceRegistry;
import codex.type.Str;
import codex.utils.Language;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {
    
    public  final static String PID = "PID";
    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private final Class        entityClass;
    private final List<String> dynamicProps = new LinkedList<>();
    private final UndoRegistry undoRegistry = new UndoRegistry();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners = new LinkedList<>();
    
    EntityModel(Class entityClass, String PID) {
        this.entityClass = entityClass;
        addProperty(EntityModel.PID, new Str(PID), true, Access.Edit);
        init(PID);
    }
    
    public final void init(String PID) {
        if (PID != null) {
            STORE.initClassInstance(entityClass, PID);
        }
    }

    @Override
    public final boolean isValid() {
        boolean isValid = true;
        for (String propName : getProperties(Access.Any)) {
            isValid = isValid & !(getValue(propName) == null && getProperty(propName).isRequired()) ;
        }
        return isValid;
    };
    
    @Override
    final void addProperty(String name, IComplexType value, boolean require, Access restriction) {
        super.addProperty(name, value, require, restriction);
        getProperty(name).addChangeListener(this);
    }
    
    /**
     * Добавление хранимого свойства в сущность.
     * @param name Идентификатор свойства.
     * @param value Начальное значение свойства.
     * @param require Признак того что свойство должно иметь значение.
     * @param restriction  Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     */
    public final void addUserProp(String name, IComplexType value, boolean require, Access restriction) {
        STORE.addClassProperty(entityClass, name);
        STORE.readClassProperty(entityClass, (String) getValue(PID), name, value);
            
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
                public void modelChanged(EntityModel model, List<String> changes) {
                    List<String> intersection = new LinkedList<>(dynamicProps);
                    intersection.retainAll(Arrays.asList(baseProps));
                    if (!intersection.isEmpty()) {
                        setValue(name, valueProvider.get());
                    }
                }
                @Override
                public void modelSaved(EntityModel model, List<String> changes) {
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
            listener.modelChanged(this, getChanges());
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
     * Получить наименование свойства.
     */
    public final String getPropertyTitle(String name) {
        return getProperty(name).getTitle();
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
        boolean success = STORE.updateClassInstance(entityClass, 
                (String) getValue(PID),
                changes.stream()
                        .map((propName) -> {
                            return getProperty(propName);
                        })
                        .collect(Collectors.toList())
        );
        if (success) {
            undoRegistry.clear();
            modelListeners.forEach((listener) -> {
                listener.modelSaved(this, changes);
            });
        } else {
            MessageBox msgBox = new MessageBox(
                    MessageType.ERROR, null,
                    Language.get("error@notsaved"),
                    null
            );
            msgBox.setVisible(true);
        }
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
            listener.modelRestored(this, changes);
        });
    }

    @Override
    public IEditor getEditor(String name) {
        IEditor editor = super.getEditor(name);
        addModelListener(new IModelListener() {
            
            @Override
            public void modelSaved(EntityModel model, List<String> changes) { 
                editor.getLabel().setText(getProperty(name).getTitle());
            }

            @Override
            public void modelChanged(EntityModel model, List<String> changes) {
                editor.getLabel().setText(getProperty(name).getTitle() + (changes.contains(name) ? " *" : ""));
            }
            
        });
        editor.setEditable(!dynamicProps.contains(name));
        return editor;
    }

}
