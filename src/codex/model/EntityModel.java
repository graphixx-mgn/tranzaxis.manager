package codex.model;

import codex.command.EditorCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import static codex.config.IConfigStoreService.RC_SUCCESS;
import codex.editor.IEditor;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.mask.IMask;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Int;
import codex.type.Str;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Arrays;
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
    
    public  final static String ID  = "ID";  // Primary unique identifier
    public  final static String OWN = "OWN"; // Reference to owner entity
    public  final static String SEQ = "SEQ"; // Order sequence number
    public  final static String PID = "PID"; // Title or name 
    public  final static String OVR = "OVR"; // List of overridden values
    public  final static String DEL = "DEL"; // Entity must be deleted
    
    public  final static List<String> SYSPROPS = Arrays.asList(new String[] {ID, OWN, SEQ, PID, OVR, DEL});
    
    private final static IConfigStoreService    CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private final Map<String, String> databaseValues;
    
    private final Class           entityClass;
    private final DynamicResolver dynamicResolver = new DynamicResolver();
    private final List<String>    dynamicProps = new LinkedList<>();
    private final UndoRegistry    undoRegistry = new UndoRegistry();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners = new LinkedList<>();
    
    EntityModel(EntityRef owner, Class entityClass, String PID) {
        this.entityClass = entityClass;
        this.databaseValues = CAS.readClassInstance(entityClass, PID, owner.getId());
        
        addDynamicProp(
                ID, 
                new Int(databaseValues.get(ID) != null ? Integer.valueOf(databaseValues.get(ID)) : null), 
                Access.Any, null
        );
        
        addDynamicProp(
                OWN, 
                owner, 
                Access.Any, null
        );
        addUserProp(SEQ, new Int(null), 
                !Catalog.class.isAssignableFrom(entityClass), 
                Access.Any
        );
        addUserProp(EntityModel.PID, new Str(PID),  
                true, 
                Catalog.class.isAssignableFrom(entityClass) ? Access.Any : null
        );
        setPropUnique(EntityModel.PID);
        addUserProp(EntityModel.OVR, new ArrStr(new LinkedList<>()), false, Access.Any);
        addModelListener(dynamicResolver);
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
            isValid = isValid & getProperty(propName).isValid();
        }
        return isValid;
    };
    
    protected final void setOwner(Entity owner) {
        EntityRef ownerRef = new EntityRef(owner.getClass());
        ownerRef.setValue(owner);
        setValue(OWN, ownerRef);
    }
    
    public final Entity getOwner() {
        return (Entity) getValue(OWN);
    }
    
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
        addProperty(name, value, require, restriction);
        if (databaseValues != null && databaseValues.get(name) != null) {
            getProperty(name).getPropValue().valueOf(databaseValues.get(name));
        }
    }
    
    /**
     * Добавление хранимого свойства в сущность.
     * @param propHolder Ссылка на свойство.
     * @param restriction  Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     */
    public final void addUserProp(PropertyHolder propHolder, Access restriction) {
        addProperty(propHolder, restriction);
        if (databaseValues != null && databaseValues.get(propHolder.getName()) != null) {
            getProperty(propHolder.getName()).getPropValue().valueOf(databaseValues.get(propHolder.getName()));
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
    
    public final void updateDynamicProp(String... names) {
        for (String name : names) {
            if (dynamicProps.contains(name)) {
                Object dynValue = dynamicResolver.valueProviders.get(name).get();
                dynamicResolver.propertyHolders.get(name).setValue(dynValue);
            }
        }
    }
    
    /**
     * Значение свойства должно быть уникальным среди сушностей у одного родителя
     * @param name Идентификатор свойства.
     */
    public final void setPropUnique(String name) {
        getProperty(name).getPropValue().setMask(new UniqueMask(name));
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
        new LinkedList<>(changeListeners).forEach((listener) -> {
            listener.propertyChange(name, oldValue, newValue);
        });
        new LinkedList<>(modelListeners).forEach((listener) -> {
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
            IMask mask = getProperty(name).getPropValue().getMask();
            if (
                    (value == null && (mask == null || !mask.notNull())) || 
                    (mask == null || mask.verify(value))
            ) {
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
    
    public final boolean existReferencies() {
        List<IConfigStoreService.ForeignLink> links = CAS.findReferencedEntries(entityClass, getID());
        if (links.isEmpty()) {
            return false;
        } else {
            StringBuilder msgBuilder = new StringBuilder(
                    MessageFormat.format(Language.get("error@notdeleted"), getPID())
            );
            links.forEach((link) -> {
                try {
                    if (link.isIncoming) {
                        Entity referenced = EAS.getEntity(Class.forName(link.entryClass), link.entryID);
                        msgBuilder
                                .append(Language.get("link@incoming"))
                                .append(referenced.getPathString());
                    } else {
                        msgBuilder
                                .append(Language.get("link@outgoing"))
                                .append(link.entryPID)
                                ;
                    }
                } catch (ClassNotFoundException e) {}
            });
            MessageBox.show(MessageType.ERROR, msgBuilder.toString());
            return true;
        }
    }
    
    public final boolean remove() {
        if (existReferencies()) {
            return false;
        } else {
            int result = CAS.removeClassInstance(entityClass, getID());
            if (result == IConfigStoreService.RC_SUCCESS) {
                new LinkedList<>(modelListeners).forEach((listener) -> {
                    listener.modelDeleted(this);
                });
            }
            return result == IConfigStoreService.RC_SUCCESS;
        }
    }
    
    /**
     * Сохранение изменений модели.
     */
    public final void commit() {
        List<String> changes = getChanges();
        if (!changes.isEmpty()) {
            Map<String, IComplexType> values = new LinkedHashMap<>();
            changes.forEach((propName) -> {
                
                // Create instance of referenced object if not exists
                if (getProperty(propName).getPropValue() instanceof EntityRef) {
                    EntityRef ref = ((EntityRef) getProperty(propName).getPropValue());
                    
                    if (ref.isLoaded() && ref.getId() == null) {
                        Entity owner = ref.getValue().model.getOwner();
                        Map<String, IComplexType> propDefinitions = new HashMap() {{
                            put(OWN, owner == null ? null : owner.toRef());
                            put(DEL, new Bool(true)); // Auto delete
                        }};
                        
                        Map<String, Integer> keys = CAS.initClassInstance(
                                ref.getEntityClass(), 
                                ref.getValue().model.getPID(), 
                                propDefinitions,
                                owner == null ? null : owner.model.getID()
                        );
                        ref.getValue().model.setValue(ID, keys.get(ID));
                        ref.getValue().model.setValue(SEQ, keys.get(SEQ));
                        
                        CAS.updateClassInstance(ref.getEntityClass(), keys.get(ID), propDefinitions);
                    }
                }
                
                values.put(propName, getProperty(propName).getPropValue());
            });
            if (getID() == null || !databaseValues.keySet().containsAll(changes)) {
                Map<String, IComplexType> propDefinitions = new LinkedHashMap<>();
                
                propDefinitions.put(OWN, getProperty(OWN).getPropValue());
                values.put(OWN, getProperty(OWN).getPropValue());
                
                getProperties(Access.Any)
                    .stream()
                    .filter((propName) -> {
                        return !dynamicProps.contains(propName) && !SYSPROPS.contains(propName);
                    })
                    .forEach((propName) -> {
                        propDefinitions.put(propName, getProperty(propName).getPropValue());
                    });
                
                EntityRef ownerRef = (EntityRef) getProperty(OWN).getPropValue();
                Integer ownerId = ownerRef.getValue() != null ? ownerRef.getValue().model.getID() : null;
                Map<String, Integer> keys = CAS.initClassInstance(entityClass, getPID(), propDefinitions, ownerId);
                setValue(ID, keys.get(ID));
                setValue(SEQ, keys.get(SEQ));
            }
            int updateResult = CAS.updateClassInstance(entityClass, getID(), values);
            if (updateResult == IConfigStoreService.RC_SUCCESS) {
                
                // Delete instance of referenced object if necessary (DEL = "1")
                changes.forEach((propName) -> {    
                    if (getProperty(propName).getPropValue() instanceof EntityRef) {
                        Entity prevValue = (Entity) undoRegistry.previous(propName);
                        if (prevValue != null) {
                            if ("1".equals(CAS.readClassInstance(prevValue.getClass(), prevValue.model.getID()).get(DEL))) {
                                int deleteResult = CAS.removeClassInstance(prevValue.getClass(), prevValue.model.getID());
                                if (deleteResult == RC_SUCCESS) {
                                    prevValue.model.setValue(ID, null);
                                    prevValue.model.setValue(SEQ, null);
                                }
                            }
                        }
                    }
                });
                
                undoRegistry.clear();
                new LinkedList<>(modelListeners).forEach((listener) -> {
                    listener.modelSaved(this, changes);
                });
                values.keySet().forEach((propName) -> {
                    ((List<EditorCommand>) getEditor(propName).getCommands()).forEach((command) -> {
                        command.activate();
                    });
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
    
    /**
     * Сохранение значения единичного свойства.
     * @param propName 
     */
//    public final void saveValue(String propName) {
//        Map<String, String> values = new LinkedHashMap();
//        values.put(propName, getProperty(propName).getPropValue().toString());
//        int result = CAS.updateClassInstance(entityClass, getID(), values);
//        if (result != IConfigStoreService.RC_SUCCESS) {
//            MessageBox.show(MessageType.ERROR, Language.get(EntityModel.class.getSimpleName(), "error@notsaved"));
//        } else {
//            if (undoRegistry.exists(propName)) {
//                undoRegistry.put(
//                        propName, 
//                        undoRegistry.previous(propName),
//                        undoRegistry.previous(propName)
//                );
//            }
//        }
//    }

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
        if (dynamicProps.contains(name)) {
            editor.setEditable(false);
        }
        return editor;
    }

    
    final class DynamicResolver implements IPropertyChangeListener, IModelListener {

        final List<String> resolveOrder = new LinkedList<>();
        final Map<String, String> resolveMap = new HashMap<>();
        final Map<String, Supplier> valueProviders = new HashMap<>();
        final Map<String, PropertyHolder> propertyHolders = new HashMap<>();
        
        private final IModelListener referenceListener = new IModelListener() {
            
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                DynamicResolver.this.modelSaved(
                        EntityModel.this, 
                        new LinkedList<>(resolveOrder)
                            .stream()
                            .filter((propName) -> {
                                if (getPropertyType(propName) == EntityRef.class && getValue(propName) == null) {
                                }
                                return 
                                        getPropertyType(propName) == EntityRef.class &&
                                        getValue(propName) != null &&
                                        ((Entity) getValue(propName)).model.equals(model);
                            })
                            .collect(Collectors.toList())
                );
            }                    

        };

        PropertyHolder newProperty(String name, IComplexType value, Supplier valueProvider, String... baseProps) {
            
            for (String basePropName : baseProps) {
                if (resolveOrder.contains(name)) {
                    int propIdx = resolveOrder.indexOf(name);
                    resolveOrder.add(propIdx, basePropName);
                } else {
                    resolveOrder.add(basePropName);
                }
                resolveMap.put(basePropName, name);
                
                if (getPropertyType(basePropName) == EntityRef.class) {
                    properties.get(basePropName).addChangeListener(this);
                    if (((EntityRef) getProperty(basePropName).getPropValue()).getValue() != null) {
                        ((EntityRef) getProperty(basePropName).getPropValue()).getValue().model.addModelListener(referenceListener);
                    }
                }
            }
            valueProviders.put(name, (Supplier<Object>) () -> {
                return valueProvider.get();
            });
            
            AtomicBoolean propInitiated = new AtomicBoolean(false);
            propertyHolders.put(name, new PropertyHolder(name, value, false) {
                @Override
                public IComplexType getPropValue() {
                    if (!propInitiated.getAndSet(true)) {
                        value.setValue(valueProviders.get(name).get());
                    }
                    return value;
                }
            });
            propertyHolders.get(name).addChangeListener(this);
            return propertyHolders.get(name);
        };

        @Override
        public void propertyChange(String name, Object oldValue, Object newValue) {
            if (getPropertyType(name) == EntityRef.class) {
                if (oldValue != null) {
                    ((Entity) oldValue).model.removeModelListener(referenceListener);
                }
                if (newValue != null) {
                    ((Entity) newValue).model.addModelListener(referenceListener);
                }
            }
            if (resolveOrder.contains(name) && EntityModel.this.isPropertyDynamic(name)) {
                String dynamicProp = resolveMap.get(name);
                Object dynValue = valueProviders.get(dynamicProp).get();
                propertyHolders.get(dynamicProp).setValue(dynValue);
            }
        }

        @Override
        public void modelSaved(EntityModel model, List<String> changes) {
            resolveOrder.stream()
                    .filter((propName) -> {
                        return changes.contains(propName);
                    })
                    .map((baseProp) -> {
                        return resolveMap.get(baseProp);
                    })
                    .distinct()
                    .forEach((dynamicProp) -> {                        
                        Object dynValue = valueProviders.get(dynamicProp).get();
                        propertyHolders.get(dynamicProp).setValue(dynValue);
                    });
        }
    }
    
    private final class UniqueMask implements IMask<String> {
        
        private final String ERROR = Language.get(
                SelectorPresentation.class.getSimpleName(), 
                "creator@pid.hint"
        );
        private final String propName;
        
        public UniqueMask(String propName) {
            this.propName = propName;
        }

        @Override
        public boolean verify(String value) {
            return value != null &&
                !EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .filter((entity) -> {
                        return entity.model != EntityModel.this;
                    })
                    .anyMatch((entity) -> {
                        return entity.model.getProperty(propName).getPropValue().toString().equals(value);
                    });
        }

        @Override
        public String getErrorHint() {
            return ERROR;
        }

        @Override
        public boolean notNull() {
            return true;
        }

    }
}
