package codex.model;

import codex.command.EditorCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.IEditor;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.log.Logger;
import codex.mask.IMask;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.ArrStr;
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
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {
    
    final static String ID  = "ID";  // Primary unique identifier
    final static String OWN = "OWN"; // Reference to owner entity
    final static String SEQ = "SEQ"; // Order sequence number
    final static String PID = "PID"; // Title or name 
    final static String OVR = "OVR"; // List of overridden values
    
    private static final Boolean      DEV_MODE  = "1".equals(java.lang.System.getProperty("showSysProps"));
    public  final static List<String> SYSPROPS  = Arrays.asList(new String[] {ID, OWN, SEQ, PID, OVR});
    private final static List<String> GENERATED = Arrays.asList(new String[] {ID, SEQ});
    
    private final static IConfigStoreService    CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private final Class           entityClass;
    private final DynamicResolver dynamicResolver = new DynamicResolver();
    private final List<String>    dynamicProps    = new LinkedList<>();
    private final UndoRegistry    undoRegistry    = new UndoRegistry();
    private final Map<String, String>           databaseValues;
    private final Map<String, Object>           initialValues   = new HashMap<>();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners  = new LinkedList<>();
    
    EntityModel(EntityRef owner, Class entityClass, String PID) {
        this.entityClass = entityClass;
        Integer ownerId = owner == null ? null : owner.getId();
        this.databaseValues = CAS.readClassInstance(entityClass, PID, ownerId);
        
        initialValues.put(EntityModel.ID,  null);
        initialValues.put(EntityModel.SEQ, null);
        initialValues.put(EntityModel.PID, PID);
        initialValues.put(EntityModel.OWN, owner == null ? null : owner.getValue());
        initialValues.put(EntityModel.OVR, null);
        
        addDynamicProp(
                ID, 
                new Int(databaseValues.get(ID) != null ? Integer.valueOf(databaseValues.get(ID)) : null), 
                DEV_MODE ? null : Access.Any, null
        );
        addUserProp(SEQ, 
                new Int(databaseValues.get(SEQ) != null ? Integer.valueOf(databaseValues.get(SEQ)) : null), 
                !Catalog.class.isAssignableFrom(entityClass), 
                DEV_MODE ? Access.Select : Access.Any
        );
        addUserProp(EntityModel.PID, new Str(PID),  
                true,
                DEV_MODE ? null : (
                    Catalog.class.isAssignableFrom(entityClass) ? Access.Any : null
                )
        );
        addUserProp(EntityModel.OWN, owner != null ? owner : new EntityRef(null),
                false, 
                DEV_MODE ? null : Access.Any
        );
        addUserProp(
                EntityModel.OVR, 
                new ArrStr(databaseValues.get(OVR) != null ? ArrStr.parse(databaseValues.get(OVR)) : new LinkedList<>()),
                false, 
                DEV_MODE ? Access.Select : Access.Any
        );
        
        addPropertyGroup("System properties", ID, SEQ, OWN, OVR);
        setPropUnique(EntityModel.PID);
    }
    
    final Integer getID() {
        return (Integer) getValue(ID);
    }
    
    final String getPID(boolean unsaved) {
        return (String) (unsaved ? getUnsavedValue(PID) : getValue(PID));
    }
    
    final Integer getSEQ() {
        return (Integer) getValue(SEQ);
    }
    
    final Entity getOwner() {
        return (Entity) getValue(OWN);
    }
    
    final List<String> getOverride() {
        return (List<String>) getValue(OVR);
    }
    
    final EntityModel setID(Integer id) {
        setValue(ID, id);
        return this;
    }
    
    final EntityModel setPID(String pid) {
        setValue(PID, pid);
        return this;
    }
    
    final EntityModel setSEQ(Integer seq) {
        setValue(SEQ, seq);
        return this;
    }

    public EntityModel setOverride(List<String> value) {
        setValue(OVR, value);
        return this;
    }
    
    public String getQualifiedName() {
        return MessageFormat.format(
                "[{0}/#{1}-''{2}'']", 
                entityClass.getSimpleName(), 
                getID() == null ? "?" : getID(),
                IComplexType.coalesce(getPID(getID() != null), "<new>")
        );
    }
    
    @Override
    public final boolean isValid() {
        boolean isValid = true;
        for (String propName : getProperties(Access.Any)) {
            if (!propName.equals(EntityModel.OWN)) {
                isValid = isValid & getProperty(propName).isValid();
            }
        }
        return isValid;
    };
    
    @Override
    protected void addProperty(PropertyHolder propHolder, Access restriction) {
        if (!initialValues.containsKey(propHolder.getName())) {
            initialValues.put(
                    propHolder.getName(), 
                    propHolder.getOwnPropValue().getValue()
            );
        }
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
     * Добавление опционального свойства в сущность.
     * @param name Идентификатор свойства.
     * @param value Начальное значение свойства.
     * @param require Признак того что свойство должно иметь значение.
     * селекторе.
     */
    public final void addExtraProp(String name, IComplexType value, boolean require) {
        addProperty(name, value, require, Access.Extra);
        if (databaseValues != null && databaseValues.get(name) != null) {
            getProperty(name).getPropValue().valueOf(databaseValues.get(name));
        }
    }

    public final boolean hasExtraProps() {
        return restrictions.values().contains(Access.Extra);
    }

    public final boolean isPropertyExtra(String propName) {
        if (!properties.containsKey(propName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Model already has property ''{0}''", propName)
            );
        }
        return Access.Extra.equals(restrictions.get(propName));
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
     * Обновление всех динамических свойств модели.
     */
    public final void updateDynamicProps() {
        dynamicResolver.updateDynamicProps(dynamicProps.stream()
                .filter((propName) -> {
                    return dynamicResolver.valueProviders.containsKey(propName);
                }).collect(Collectors.toList())
        );
    }
    
    /**
     * Обновление динамических свойств модели.
     * @param names Список свойст для обновления.
     */
    public final void updateDynamicProps(String... names) {
        dynamicResolver.updateDynamicProps(Arrays.asList(names).stream()
                .filter((propName) -> {
                    return dynamicProps.contains(propName) && dynamicResolver.valueProviders.containsKey(propName);
                }).collect(Collectors.toList())
        );
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
    public final synchronized void addChangeListener(IPropertyChangeListener listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Добавление слушателя событии модели.
     */
    public final synchronized void addModelListener(IModelListener listener) {
        if (!modelListeners.contains(listener)) {
            modelListeners.add(listener);
        }
    }
    
    /**
     * Удаление слушателя событии модели.
     */
    public final synchronized void removeModelListener(IModelListener listener) {
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
        return getProperty(name).getType();
    }
    
    /**
     * Получить наименование свойства.
     */
    public final String getPropertyTitle(String name) {
        return getProperty(name).getTitle();
    }
    
    public final boolean isPropertyDynamic(String propName) {
        if (!properties.containsKey(propName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Model already has property ''{0}''", propName)
            );
        }
        return dynamicProps.contains(propName);
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
                .filter((name) -> !dynamicProps.contains(name) && undoRegistry.exists(name))
                .collect(Collectors.toList());
    }
    
    public final boolean existReferencies() {
        Logger.getLogger().debug("Check references for entity ''{0}''", getQualifiedName());
        List<IConfigStoreService.ForeignLink> links = CAS.findReferencedEntries(entityClass, getID());
        links.removeIf(link -> {
                try {
                    if (link.isIncoming) {
                        return false;
                    } else {
                        EntityRef ref = EntityRef.build(Class.forName(link.entryClass), link.entryID);
                        if (ref.getValue() != null && ref.getValue().isAutoGenerated()) {
                            List<IConfigStoreService.ForeignLink> depends = CAS.findReferencedEntries(Class.forName(link.entryClass), link.entryID);
                            if (depends.isEmpty()) {
                                Logger.getLogger().debug("Skip auto generated independent entity ''{0}''", ref.getValue().model.getQualifiedName());
                            }
                            return depends.isEmpty();
                        } else {
                            return false;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    return true;
                }
        });
        if (links.isEmpty()) {
            return false;
        } else {
            Logger.getLogger().debug(MessageFormat.format(
                    "Entity ''{0}'' has references:\n{1}",
                    getQualifiedName(),
                    links.stream()
                        .map(link -> {
                            Entity referenced = null;
                            try {
                                referenced = EAS.getEntity(Class.forName(link.entryClass), link.entryID);
                            } catch (ClassNotFoundException e) {
                                //
                            }
                            return MessageFormat.format(
                                    "* {0}: {1}",
                                    link.isIncoming ? "incoming" : "outgoing",
                                    referenced != null ? referenced.getPathString() : link.entryPID
                            );
                        }).collect(Collectors.joining("\n"))
            ));
            MessageBox.show(
                    MessageType.ERROR,
                    MessageFormat.format(Language.get("error@notdeleted"), getPID(false)).concat(
                            links.stream()
                                    .map(link -> {
                                        Entity referenced = null;
                                        try {
                                            referenced = EAS.getEntity(Class.forName(link.entryClass), link.entryID);
                                        } catch (ClassNotFoundException e) {
                                            //
                                        }
                                        return MessageFormat.format(
                                                "{0}{1}",
                                                link.isIncoming ? Language.get("link@incoming") : Language.get("link@outgoing"),
                                                referenced != null ? referenced.getPathString() : link.entryPID
                                        );
                                    }).collect(Collectors.joining())
                    )
            );
            return true;
        }
    }

    public boolean commit(boolean showError, String... propNames) throws Exception {
        if (propNames != null && propNames.length > 0) {
            if (getID() == null) {
                throw new IllegalStateException(MessageFormat.format("Update failed: model {0} does not exist in database", getQualifiedName()));
            } else {
                List<String> updateProps = Arrays.asList(propNames);
                Logger.getLogger().debug(MessageFormat.format("Perform partial commit model {0} {1}", getQualifiedName(), updateProps));
                return update(showError, updateProps);
            }
        }
        return true;
    }
    
    /**
     * Сохранение изменений модели.
     */
    public final void commit(boolean showError) throws Exception {
        List<String> changes = getChanges();
        if (!changes.isEmpty()) {
            if (getID() == null) {
                Logger.getLogger().debug(MessageFormat.format("Perform commit (create) model {0}", getQualifiedName()));
                if (create(showError)) {
                    update(showError, changes);
                }
            } else {
                if (maintenance(showError)) {
                    Logger.getLogger().debug(MessageFormat.format("Perform commit (update) model {0}", getQualifiedName()));
                    update(showError, changes);
                }
            }
        }
    }
    
    /**
     * Откат изменений модели.
     */
    public final void rollback() {
        List<String> changes = getChanges();
        if (!changes.isEmpty()) {
            Logger.getLogger().debug(MessageFormat.format("Perform rollback model {0}", getQualifiedName()));
            changes.forEach((name) -> {
                if (undoRegistry.exists(name)) {
                    getProperty(name).setValue(undoRegistry.previous(name));
                }
            });
            new LinkedList<>(modelListeners).forEach((listener) -> listener.modelRestored(this, changes));
        }
    }

    /**
     * Частичный откат изменений модели.
     * @param propNames Список свойств для отката.
     */
    public final void rollback(String... propNames) {
        if (propNames != null && propNames.length > 0) {
            List<String> changes = getChanges().stream()
                    .filter(propName -> Arrays.asList(propNames).contains(propName))
                    .collect(Collectors.toList());
            if (!changes.isEmpty()) {
                Logger.getLogger().debug(MessageFormat.format("Perform partial rollback model {0} {1}", getQualifiedName(), changes));
                changes.forEach((name) -> getProperty(name).setValue(undoRegistry.previous(name)));
                new LinkedList<>(modelListeners).forEach((listener) -> listener.modelRestored(this, changes));
            }
        }
    }
    
    private static Map<String, IComplexType> getPropDefinitions(EntityModel model) {
        Map<String, IComplexType> propDefinitions = new LinkedHashMap<>();                
        model.getProperties(Access.Any)
            .stream()
            .filter((propName) -> !model.dynamicProps.contains(propName) || EntityModel.ID.equals(propName))
            .forEach((propName) -> propDefinitions.put(propName, model.getProperty(propName).getPropValue()));
        return propDefinitions;
    }
    
    private boolean create(boolean showError) throws Exception {
        Integer ownerId = getOwner() == null ? null : getOwner().getID();
        try {
            synchronized (this) {
                CAS.initClassInstance(
                        entityClass, 
                        (String) getProperty(PID).getPropValue().getValue(), 
                        getPropDefinitions(this), 
                        ownerId
                ).forEach(this::setValue);
                return true;
            }
        } catch (Exception e) {
            Logger.getLogger().error("Unable initialize model in database", e);
            if (showError) {
                MessageBox.show(
                        MessageType.ERROR, 
                        MessageFormat.format(
                                Language.get("error@notsaved"),
                                e.getMessage()
                        )
                );
            }
            throw e;
        }
    }
    
    private boolean update(boolean showError, List<String> changes) throws Exception {
        Map<String, String> dbValues = CAS.readClassInstance(entityClass, getID());
        try {
            synchronized (this) {
                processAutoReferences(true, null);
                CAS.updateClassInstance(
                        entityClass, 
                        getID(),
                        changes.stream()
                            .filter((propName) -> !getProperty(propName).getOwnPropValue().toString().equals(dbValues.get(propName)))
                            .collect(Collectors.toMap(
                                    propName -> propName, 
                                    propName -> getProperty(propName).getOwnPropValue()
                            ))
                );
                processAutoReferences(false, null);
                changes.forEach(propName -> {
                    if (undoRegistry.exists(propName)) {
                        undoRegistry.put(propName, undoRegistry.current(propName), undoRegistry.previous(propName));
                    }
                });
            }
            new LinkedList<>(modelListeners).forEach((listener) -> {
                listener.modelSaved(this, new LinkedList<>(changes));
            });
            changes.forEach((propName) -> {
                ((List<EditorCommand>) getEditor(propName).getCommands()).forEach((command) -> {
                    command.activate();
                });
            });
            return true;
        } catch (Exception e) {
            Logger.getLogger().error("Unable update model properties to database", e);
            if (showError) {
                MessageBox.show(
                        MessageType.ERROR, 
                        MessageFormat.format(
                                Language.get("error@notsaved"),
                                e.getMessage()
                        )
                );
            }
            throw e;
        }
    }
    
    void processAutoReferences(boolean create, List<String> propList) throws Exception {
        Set<Entity> autoReferences = (propList == null ? getChanges() : propList).stream()
                .filter((propName) -> {
                    return
                            EntityRef.class.isAssignableFrom(getPropertyType(propName)) &&
                            (create ? getUnsavedValue(propName) : getValue(propName)) != null;
                }).map((propName) -> {
                    return (Entity) (create ? getUnsavedValue(propName) : getValue(propName));
                }).filter((entity) -> {
                    return entity.isAutoGenerated() && (
                                ( create && entity.getID() == null) ||
                                (!create && entity.getID() != null && CAS.findReferencedEntries(entity.getClass(), entity.getID()).isEmpty())
                           );
                })
                .collect(Collectors.toSet());
        if (!autoReferences.isEmpty() && create) {
            for (Entity entity : autoReferences) {
                synchronized (entity) {
                    Entity owner = entity.getOwner();
                    boolean exists = CAS.isInstanceExists(entity.getClass(), entity.getPID(), owner == null ? null : owner.getID());
                    if (!exists) {
                        Logger.getLogger().debug(
                                "Perform automatic entity creation: {0}",
                                entity.model.getQualifiedName()
                        );
                        CAS.initClassInstance(
                                entity.getClass(),
                                entity.getPID(),
                                getPropDefinitions(entity.model),
                                owner == null ? null : owner.getID()
                        ).forEach(entity.model::setValue);
                    } else {
                        Logger.getLogger().debug(
                                "Skip automatic entity creation: {0} [Already exists]",
                                entity.model.getQualifiedName()
                        );
                    }
                }
            }
        } else if (!autoReferences.isEmpty()) {
            for (Entity entity : autoReferences) {
                synchronized (entity) {
                    Entity owner = entity.getOwner();
                    boolean exists = CAS.isInstanceExists(entity.getClass(), entity.getPID(), owner == null ? null : owner.getID());
                    if (exists) {
                        Logger.getLogger().debug(
                                "Perform automatic entities deletion: {0}",
                                entity.model.getQualifiedName()
                        );
                        entity.model.remove();
                    } else {
                        Logger.getLogger().debug(
                                "Skip automatic entity deletion: {0} [Already deleted]",
                                entity.model.getQualifiedName()
                        );
                    }
                }
            }
        }
    }
    
    /**
     * Перечитывание значений свойств из БД или установка начальных значений 
     * если запись в БД отсутствует.
     */
    public void read() {
        Map<String, String> dbValues;
        Integer ownerId = getOwner() == null ? null : getOwner().getID();
        if (getID() == null) {
            dbValues = CAS.readClassInstance(entityClass, getPID(false), ownerId);
        } else {
            dbValues = CAS.readClassInstance(entityClass, getID());
        }
        List<String> userProps = getProperties(Access.Any).stream()
                .filter((propName) -> {
                    return !isPropertyDynamic(propName) || SYSPROPS.contains(propName);
                }).collect(Collectors.toList());
        
        userProps.forEach((propName) -> {
            Object prevValue = getProperty(propName).getOwnPropValue().getValue();
            
            if (dbValues.containsKey(propName) && dbValues.get(propName) != null) {
                getProperty(propName).getOwnPropValue().valueOf(dbValues.get(propName));
            } else if (initialValues.containsKey(propName)) {
                getProperty(propName).getOwnPropValue().setValue(initialValues.get(propName));
            }
        });
        updateDynamicProps();
    }
    
    /**
     * Удаление данных модели из БД.
     */
    public boolean remove() {
        return remove(true);
    }
    
    /**
     * Удаление данных модели из БД.
     */
    boolean remove(boolean readAfter) {
        if (existReferencies()) {
            return false;
        } else {
            Logger.getLogger().debug(MessageFormat.format("Perform removal model {0}", getQualifiedName()));
            try {
                synchronized (this) {
                    try {
                        for (IConfigStoreService.ForeignLink link : CAS.findReferencedEntries(entityClass, getID())) {
                            if (!link.isIncoming) {
                                EntityRef ref = EntityRef.build(Class.forName(link.entryClass), link.entryID);
                                if (ref.getValue() != null && ref.getValue().isAutoGenerated() && CAS.findReferencedEntries(ref.getEntityClass(), ref.getId()).isEmpty()) {
                                    Logger.getLogger().debug(MessageFormat.format("Cascade removal dependent model {0}", ref.getValue().model.getQualifiedName()));
                                    ref.getValue().model.remove(false);
                                }
                            }
                        }

                        CAS.removeClassInstance(entityClass, getID());
                        processAutoReferences(false, getProperties(Access.Any));
                    } catch (Exception e) {
                        Logger.getLogger().error("Unable delete model from database", e);
                        throw e;
                    } finally {
                        if (readAfter) {
                            read();
                        }
                    }
                }
                new LinkedList<>(modelListeners).forEach((listener) -> {
                    listener.modelDeleted(this);
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    private boolean maintenance(boolean showError) {
        List<String> propList = getProperties(Access.Any).stream().filter((propName) -> {
            return !isPropertyDynamic(propName);
        }).collect(Collectors.toList());
        
        Map<String, String> dbValues = CAS.readClassInstance(entityClass, getID());
        List<String> extraProps = dbValues.keySet().stream().filter((propName) -> {
            return !propList.contains(propName) && !EntityModel.SYSPROPS.contains(propName);
        }).collect(Collectors.toList());
        
        Map<String, IComplexType> absentProps = propList.stream()
                .filter((propName) -> {
                    return !dbValues.containsKey(propName);
                })
                .collect(Collectors.toMap(
                        propName -> propName, 
                        propName -> getProperty(propName).getPropValue()
                ));

        if (!extraProps.isEmpty() || !absentProps.isEmpty()) {
            Logger.getLogger().debug(MessageFormat.format("Perform maintenance class catalog {0}", entityClass.getSimpleName()));
            try {
                CAS.maintainClassCatalog(entityClass, extraProps, absentProps);
                return true;
            } catch (Exception e) {
                Logger.getLogger().error("CAS: Unable to maintain class catalog", e);
                if (showError) {
                    MessageBox.show(
                            MessageType.ERROR, 
                            MessageFormat.format(
                                    Language.get("error@notsaved"),
                                    e.getMessage()
                            )
                    );
                }
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public IEditor getEditor(String name) {
        boolean editorExists = editors.containsKey(name);
        IEditor editor = super.getEditor(name);
        editor.getLabel().setText(getProperty(name).getTitle() + (getChanges().contains(name) ? " *" : ""));
        
        // Editor settings
        if (DEV_MODE) {
            switch (name) {
                case EntityModel.PID:
                    editor.setEditable(!Catalog.class.isAssignableFrom(entityClass));
                    break;
                case EntityModel.SEQ:
                case EntityModel.OWN:
                case EntityModel.OVR:
                    editor.setEditable(false);
                    editor.setEditable(false);
                    editor.setEditable(false);
                    break;
            }
        }
        
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

    
    final class DynamicResolver {

        final Map<String, List<String>>   resolveMap      = new HashMap<>();
        final Map<String, Supplier>       valueProviders  = new HashMap<>();
        final Map<String, PropertyHolder> propertyHolders = new HashMap<>();

        @SuppressWarnings("unchecked")
        PropertyHolder newProperty(String name, IComplexType value, Supplier valueProvider, String... baseProps) {
            valueProviders.put(name, valueProvider);

            propertyHolders.put(name, new PropertyHolder(name, value, false) {
                private boolean initiated = false;

                @Override
                public synchronized IComplexType getPropValue() {
                    if (!initiated) {
                        initiated = true;
                        value.setValue(valueProvider.get());
                        if (baseProps != null) {
                            // User props
                            List<String> baseUserProps = Arrays.asList(baseProps).stream()
                                    .filter((basePropName) -> {
                                        return !isPropertyDynamic(basePropName);
                                    })
                                    .collect(Collectors.toList());
                            if (!baseUserProps.isEmpty()) {
                                EntityModel.this.addModelListener(new IModelListener() {
                                    @Override
                                    public void modelSaved(EntityModel model, List<String> changes) {
                                        if (changes.stream().anyMatch(baseUserProps::contains)) {
                                            setValue(valueProvider.get());
                                        }
                                    }
                                });
                            }
                            
                            // User props (referencies)
                            final IModelListener refModelListener = new IModelListener() {
                                @Override
                                public void modelSaved(EntityModel model, List<String> changes) {
                                    setValue(valueProvider.get());
                                }
                            };
                            final IPropertyChangeListener refPropListener = (name, oldValue, newValue) -> {
                                setValue(valueProvider.get());
                            };
                            
                            List<String> baseRefProps = Arrays.asList(baseProps).stream()
                                    .filter((basePropName) -> {
                                        return getPropertyType(basePropName) == EntityRef.class;
                                    })
                                    .map((baseRefPropName) -> {
                                        Entity baseEntity = (Entity) getValue(baseRefPropName);
                                        if (baseEntity != null) {
                                            baseEntity.model.addModelListener(refModelListener);
                                            baseEntity.model.properties.values().stream().filter((propHolder) -> {
                                                return baseEntity.model.dynamicProps.contains(propHolder.getName());
                                            }).forEach((propHolder) -> {
                                                propHolder.addChangeListener(refPropListener);
                                            });
                                        }
                                        return baseRefPropName;
                                    })
                                    .collect(Collectors.toList());
                            if (!baseRefProps.isEmpty()) {
                                EntityModel.this.addChangeListener((name, oldValue, newValue) -> {
                                    if (baseRefProps.contains(name)) {
                                        if (oldValue != null) {
                                            ((Entity) oldValue).model.removeModelListener(refModelListener);
                                            ((Entity) oldValue).model.properties.values().stream().filter((propHolder) -> {
                                                return ((Entity) oldValue).model.dynamicProps.contains(propHolder.getName());
                                            }).forEach((propHolder) -> {
                                                propHolder.removeChangeListener(refPropListener);
                                            });
                                        }
                                        if (newValue != null) {
                                            ((Entity) newValue).model.addModelListener(refModelListener);
                                            ((Entity) newValue).model.properties.values().stream().filter((propHolder) -> {
                                                return ((Entity) newValue).model.dynamicProps.contains(propHolder.getName());
                                            }).forEach((propHolder) -> {
                                                propHolder.addChangeListener(refPropListener);
                                            });
                                        }
                                    }
                                });
                            }
                            
                            // Dynamic props
                            List<String> baseDynProps = Arrays.asList(baseProps).stream()
                                    .filter((basePropName) -> {
                                        return isPropertyDynamic(basePropName);
                                    })
                                    .collect(Collectors.toList());
                            if (!baseDynProps.isEmpty()) {
                                EntityModel.this.addChangeListener((name, oldValue, newValue) -> {
                                    if (baseDynProps.contains(name)) {
                                        setValue(valueProvider.get());
                                    }
                                });
                                resolveMap.put(name, baseDynProps);
                            }
                        }
                    }
                    return super.getPropValue();
                }
            });
            return propertyHolders.get(name);
        }
        
        void updateDynamicProps(List<String> names) {
            if (names.size() == 1) {
                Logger.getLogger().debug(MessageFormat.format("Perform update dynamic property: {0}", names.get(0)));
                getProperty(names.get(0)).setValue(dynamicResolver.valueProviders.get(names.get(0)).get());
            } else if (names.size() > 1) {
                Map<String, List<String>> updatePlan = buildUpdatePlan(names);
                
                List<String> independentProps = updatePlan.entrySet().stream()
                            .filter((entry) -> {
                                    return 
                                        entry.getValue().size() == 1 && entry.getKey().equals(entry.getValue().get(0)) &&
                                        !updatePlan.values().stream()
                                                .filter((baseProps) -> {
                                                    return baseProps != entry.getValue();
                                                }).anyMatch((baseProps) -> {
                                                    return baseProps.contains(entry.getKey());
                                                });
                            })
                            .map((entry) -> {
                                return entry.getKey();
                            }).collect(Collectors.toList());
                
                Map<String, List<String>> dependencies = updatePlan.entrySet().stream()
                            .filter((entry) -> {
                                    return !(entry.getValue().size() == 1 && entry.getKey().equals(entry.getValue().get(0)));
                            }).collect(Collectors.toMap(
                                    entry -> entry.getKey(), 
                                    entry -> entry.getValue()
                            ));
                
                Logger.getLogger().debug(MessageFormat.format(
                        "Perform update dynamic properties: {0} by resolving plan\n"
                                + "Independent : {1}\n"
                                + "Dependencies: {2}", 
                        names,
                        independentProps,
                        dependencies.entrySet().stream()
                            .map((entry) -> {
                                return entry.getKey().concat("<=").concat(entry.getValue().toString());
                            }).collect(Collectors.joining(", "))
                ));
                
                independentProps.forEach((proName) -> {
                    getProperty(proName).setValue(dynamicResolver.valueProviders.get(proName).get());
                });
                
                Map<String, Object> newValues = new LinkedHashMap<>();
                dependencies.values().forEach((baseProps) -> {
                    baseProps.forEach((baseProp) -> {
                        if (!newValues.containsKey(baseProp)) {
                            newValues.put(baseProp, dynamicResolver.valueProviders.get(baseProp).get());
                        }
                    });
                });
                newValues.entrySet().forEach((entry) -> {
                    if (getValue(entry.getKey()) != entry.getValue()) {
                        setValue(entry.getKey(), entry.getValue());
                        dependencies.values().removeIf((baseProps) -> {
                            return baseProps.contains(entry.getKey());
                        });
                    }
                });
                if (!dependencies.isEmpty()) {
                    Logger.getLogger().debug(MessageFormat.format(
                            "Perform forced update orphaned dynamic properties: {0}", 
                            dependencies.keySet()
                    ));
                    dependencies.keySet().forEach((proName) -> {
                        getProperty(proName).setValue(dynamicResolver.valueProviders.get(proName).get());
                    });
                }
            }
        }

        private Map<String, List<String>> buildUpdatePlan(List<String> names) {
            Map<String, List<String>> plan = new LinkedHashMap<>();
            names.forEach((propName) -> {
                plan.put(propName, getBaseProps(propName));
            });
            return plan;
        }
        
        private List<String> getBaseProps(String propName) {
            List<String> chain = new LinkedList<>();
            if (!resolveMap.containsKey(propName)) {
                chain.add(propName);
            } else {
                resolveMap.get(propName).forEach((basePropName) -> {
                    chain.addAll(getBaseProps(basePropName));
                });
            }
            return chain;
        }
    }
    
    private final class UniqueMask implements IMask<String> {
        
        private final String ERROR = Language.get(SelectorPresentation.class, "creator@pid.hint");
        private final String propName;
        
        UniqueMask(String propName) {
            this.propName = propName;
        }

        @Override
        public boolean verify(String value) {
            Integer ownerId = getOwner() == null ? null : getOwner().getID();
            return value != null &&
                   !CAS.readCatalogEntries(ownerId, entityClass).keySet().stream()
                        .anyMatch((ID) -> {
                            return value.equals(CAS.readClassInstance(entityClass, ID).get(propName)) && ID != getID();
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
