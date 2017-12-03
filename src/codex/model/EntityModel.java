package codex.model;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.IEditor;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Int;
import codex.type.Str;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {
    
    public  final static String ID  = "ID";
    public  final static String SEQ = "SEQ";
    public  final static String PID = "PID";
    
    private final static IConfigStoreService    CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private final Class           entityClass;
    private final DynamicResolver dynamicResolver = new DynamicResolver();
    private final List<String>    dynamicProps = new LinkedList<>();
    private final UndoRegistry    undoRegistry = new UndoRegistry();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners = new LinkedList<>();
    
    EntityModel(Class entityClass, String PID) {
        this.entityClass = entityClass;
        addDynamicProp(EntityModel.ID, new Int(null), Access.Any, null);
        addUserProp(EntityModel.SEQ,   new Int(null), true, Access.Any);
        addUserProp(EntityModel.PID,   new Str(PID),  true, 
                Catalog.class.isAssignableFrom(entityClass) ? Access.Any : null
        );
        addModelListener(dynamicResolver);
        init();
    }
    
    public final void init() {
        if (getPID() != null) {
            Integer newID = CAS.initClassInstance(entityClass, getPID());
            if (newID != null) {
                setValue(ID, newID);
            }
            getProperty(EntityModel.SEQ).getPropValue().valueOf(CAS.readClassProperty(entityClass, getID(), EntityModel.SEQ)
            );
        }
        ((Str) getProperty(EntityModel.PID).getPropValue()).setMask(new PIDMask(entityClass, getID()));
    }
    
    public final Integer getID() {
        return (Integer) getProperty(ID).getPropValue().getValue();
    }
    
    public final String getPID() {
        return (String) getProperty(PID).getPropValue().getValue();
    }

    @Override
    public final boolean isValid() {
        boolean isValid = true;
        for (String propName : getProperties(Access.Any)) {
            isValid = isValid & !(getValue(propName) == null && getProperty(propName).isRequired());
        }
        return isValid;
    };
    
    @Override
    final void addProperty(String name, IComplexType value, boolean require, Access restriction) {
        super.addProperty(name, value, require, restriction);
    }
    
    @Override
    protected void addProperty(PropertyHolder propHolder, Access restriction) {
        super.addProperty(propHolder, restriction);
        getProperty(propHolder.getName()).addChangeListener(this);
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
        if (value instanceof EntityRef) {
            CAS.addClassProperty(entityClass, name, ((EntityRef) value).getEntityClass());
        } else {
            CAS.addClassProperty(entityClass, name, null);
        }
        addProperty(name, value, require, restriction);
        if (getID() != null) {
            String val = CAS.readClassProperty(entityClass, getID(), name);
            if (val != null) {
                getProperty(name).getPropValue().valueOf(val);
            }
        }
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
        if (valueProvider != null) {
            addProperty(
                    dynamicResolver.newProperty(name, value, valueProvider, baseProps), 
                    restriction
            );
        } else {
            addProperty(name, value, false, restriction);
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
     * Возвращает несохраненное (текущее) значение значение свойства. При этом 
     * если свойству назначена маска и согласно её значение некорректно - будет
     * возвращено сохнаненное значение.
     * @param name Имя свойства модели.
     * @deprecated
     */
    @Deprecated
    public final Object getUnsavedValue(String name) {
        if (undoRegistry.exists(name)) {
            Object value = undoRegistry.current(name);
            if (getProperty(name).getPropValue().getMask().verify(value)) {
                return value;
            } else {
                return super.getValue(name);
            }
        } else {
            return super.getValue(name);
        }
    }
    
    /**
     * Получить тип свойства по его имени.
     */
    public Class<? extends IComplexType> getPropertyType(String name) {
        return getProperty(name).getPropValue().getClass();
    }
    
    /**
     * Получить наименование свойства.
     */
    public final String getPropertyTitle(String name) {
        return getProperty(name).getTitle();
    }
    
    public final boolean isPropertyDynamic(String name) {
        return dynamicProps.contains(name);
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
    
    public final boolean remove() {
        int result = CAS.removeClassInstance(entityClass, getID());
        if (result == IConfigStoreService.RC_SUCCESS) {
            new LinkedList<>(modelListeners).forEach((listener) -> {
                listener.modelDeleted(this);
            });
        } else {
            StringBuilder msgBuilder = new StringBuilder(
                    MessageFormat.format(Language.get("error@notdeleted"), getPID())
            );
            List<IConfigStoreService.ForeignLink> links = CAS.findReferencedEntries(entityClass, getID());
            links.forEach((link) -> {
                try {
                    Entity referenced = EAS.getEntity(Class.forName(link.entryClass), link.entryID);
                    if (referenced != null) {
                        msgBuilder.append("<br>&emsp;&#9913&nbsp;&nbsp;").append(referenced.getPathString());
                    }
                } catch (ClassNotFoundException e) {}
            });
            MessageBox.show(MessageType.ERROR, msgBuilder.toString());
        }
        return result == IConfigStoreService.RC_SUCCESS;
    }
    
    /**
     * Сохранение изменений модели.
     */
    public final void commit() {
        List<String> changes = getChanges();
        if (!changes.isEmpty()) {
            Map<String, String> values = new LinkedHashMap<>();
            changes.forEach((propName) -> {
                values.put(propName, getProperty(propName).getPropValue().toString());
            });
            int result = CAS.updateClassInstance(entityClass, getID(), values);
            if (result == IConfigStoreService.RC_SUCCESS) {
                undoRegistry.clear();
                new LinkedList<>(modelListeners).forEach((listener) -> {
                    listener.modelSaved(this, changes);
                });
            } else {
                MessageBox.show(MessageType.ERROR, Language.get("error@notsaved"));
            }
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
        new LinkedList<>(modelListeners).forEach((listener) -> {
            listener.modelRestored(this, changes);
        });
    }

    @Override
    public IEditor getEditor(String name) {
        boolean editorExists = editors.containsKey(name);
        IEditor editor = super.getEditor(name);
        editor.getLabel().setText(getProperty(name).getTitle() + (getChanges().contains(name) ? " *" : ""));
        
        if (!editorExists) {
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
        }
        editor.setEditable(!dynamicProps.contains(name) && editor.isEditable());
        return editor;
    }

    
    private final class DynamicResolver implements IPropertyChangeListener, IModelListener {

        private final List<String> resolveOrder = new LinkedList<>();
        private final Map<String, String> resolveMap = new HashMap<>();
        private final Map<String, Supplier> valueProvides = new HashMap<>();
        private final Map<String, PropertyHolder> propertyHolders = new HashMap<>();

        PropertyHolder newProperty(String name, IComplexType value, Supplier valueProvider, String... baseProps) {
            
            for (String basePropName : baseProps) {
                if (resolveOrder.contains(name)) {
                    int propIdx = resolveOrder.indexOf(name);
                    resolveOrder.add(propIdx, basePropName);
                } else {
                    resolveOrder.add(basePropName);
                }
                resolveMap.put(basePropName, name);
            }
            valueProvides.put(name, valueProvider);
            
            AtomicBoolean propInitiated = new AtomicBoolean(false);
            propertyHolders.put(name, new PropertyHolder(name, value, false) {
                @Override
                public IComplexType getPropValue() {
                    if (!propInitiated.getAndSet(true)) {
                        value.setValue(valueProvider.get());
                    }
                    return value;
                }
            });
            propertyHolders.get(name).addChangeListener(this);
            
            return propertyHolders.get(name);
        };

        @Override
        public void propertyChange(String name, Object oldValue, Object newValue) {
            if (resolveOrder.contains(name) && EntityModel.this.isPropertyDynamic(name)) {
                String dynamicProp = resolveMap.get(name);
                Object dynValue = valueProvides.get(dynamicProp).get();
                propertyHolders.get(dynamicProp).setValue(dynValue);
            }
        }

        @Override
        public void modelSaved(EntityModel model, List<String> changes) {
            resolveOrder.stream()
                    .filter((changedProp) -> {
                        return changes.contains(changedProp);
                    })
                    .map((baseProp) -> {
                        return resolveMap.get(baseProp);
                    })
                    .distinct()
                    .forEach((dynamicProp) -> {
                        Object dynValue = valueProvides.get(dynamicProp).get();
                        propertyHolders.get(dynamicProp).setValue(dynValue);
                    });
        }

    }
}
