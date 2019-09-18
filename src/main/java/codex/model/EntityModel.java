package codex.model;

import codex.command.EditorCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.IConfigStoreService;
import codex.context.IContext;
import codex.editor.IEditor;
import codex.explorer.IExplorerAccessService;
import codex.log.Level;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.mask.IMask;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {

    public final static String ID   = "ID";   // Primary unique identifier
    public final static String OWN  = "OWN";  // Reference to owner entity
    public final static String SEQ  = "SEQ";  // Order sequence number
    public final static String PID  = "PID";  // Title or name
    public final static String OVR  = "OVR";  // List of overridden values
    public final static String THIS = "THIS"; // Reference to the entity object
    
    private static final Boolean      DEV_MODE  = "1".equals(java.lang.System.getProperty("showSysProps"));
    public  final static List<String> SYSPROPS  = Arrays.asList(ID, OWN, SEQ, PID, OVR);
    
    private final Class<? extends Entity>       entityClass;
    private final List<String>                  bootProps;
    private final DynamicResolver               dynamicResolver = new DynamicResolver();
    private final List<String>                  dynamicProps    = new LinkedList<>();
    private final UndoRegistry                  undoRegistry    = new UndoRegistry();
    private final Map<String, String>           databaseValues;
    private final Map<String, Object>           initialValues   = new HashMap<>();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IModelListener>          modelListeners  = new LinkedList<>();

    // Контексты
    @LoggingSource()
    @IContext.Definition(id = "ORM", name = "Object-relational mapping", icon = "/images/modify.png")
    static class OrmContext implements IContext {
        static void debug(String message, Object... params) {
            Logger.getLogger().log(Level.Debug, MessageFormat.format(message, params));
        }
        static void log(Level level, String message) {
            Logger.getLogger().log(level, message);
        }
        static void log(Level level, String message, Object... params) {
            Logger.getLogger().log(level, MessageFormat.format(message, params));
        }
        static void error(String message, Throwable exception) {
            Logger.getLogger().error(message, exception);
        }
    }
    
    EntityModel(EntityRef owner, Class<? extends Entity> entityClass, String PID) {
        this.entityClass = entityClass;
        this.bootProps   = getBootProps();

        Integer ownerId = owner == null ? null : owner.getId();
        this.databaseValues = getConfigService().readClassInstance(entityClass, PID, ownerId);
        
        initialValues.put(EntityModel.ID,  null);
        initialValues.put(EntityModel.SEQ, null);
        initialValues.put(EntityModel.PID, PID);
        initialValues.put(EntityModel.OWN, owner == null ? null : owner.getValue());
        initialValues.put(EntityModel.OVR, null);
        
        addDynamicProp(
                ID, 
                new Int(databaseValues.get(ID) != null ? Integer.valueOf(databaseValues.get(ID)) : null), 
                Access.Any, null
        );
        addUserProp(SEQ, 
                new Int(databaseValues.get(SEQ) != null ? Integer.valueOf(databaseValues.get(SEQ)) : null), 
                !Catalog.class.isAssignableFrom(entityClass), 
                DEV_MODE ? Access.Select : Access.Any
        );
        addUserProp(EntityModel.PID, new Str(PID),  
                true,
                Catalog.class.isAssignableFrom(entityClass) ? Access.Any : Access.Select
        );
        addUserProp(EntityModel.OWN, owner != null ? owner : new EntityRef<>(null),
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

    public void addBootProp(String propName) {
        bootProps.add(propName);
    }

    private List<String> getBootProps() {
        return Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> field.getAnnotation(Bootstrap.BootProperty.class) != null)
                .map(field -> {
                    field.setAccessible(true);
                    try {
                        return (String) field.get(this);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .collect(Collectors.toList());
    }

    private IConfigStoreService getConfigService() {
        return ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
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
    }
    
    @Override
    protected void addProperty(PropertyHolder propHolder, Access restriction) {
        if (!initialValues.containsKey(propHolder.getName())) {
            initialValues.put(
                    propHolder.getName(), 
                    propHolder.getOwnPropValue().getValue()
            );
        }
        super.addProperty(propHolder, restriction);
        propHolder.addChangeListener(this);
        if (bootProps.contains(propHolder.getName())) {
            Bootstrap.setProperty(
                    entityClass,
                    getPID(false),
                    propHolder.getName(),
                    propHolder.getOwnPropValue().toString()
            );
        }
    }
    
    /**
     * Добавление хранимого свойства в сущность.
     * @param name Идентификатор свойства.
     * @param value Начальное значение свойства.
     * @param require Признак того что свойство должно иметь значение.
     * @param restriction  Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     */
    public final void addUserProp(String name, ISerializableType value, boolean require, Access restriction) {
        PropertyHolder propHolder = new PropertyHolder<>(name, value, require);
        if (databaseValues != null && databaseValues.get(name) != null) {
            propHolder.getPropValue().valueOf(databaseValues.get(name));
        }
        addProperty(propHolder, restriction);
    }
    
    /**
     * Добавление хранимого свойства в сущность.
     * @param propHolder Ссылка на свойство.
     * @param restriction  Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     */
    public final void addUserProp(PropertyHolder<? extends ISerializableType, ?> propHolder, Access restriction) {
        if (!ISerializableType.class.isAssignableFrom(propHolder.getType())) {
            throw new IllegalStateException("It is not allowed to create user property of not serializable type '"+propHolder.getType()+"'");
        }
        if (databaseValues != null && databaseValues.get(propHolder.getName()) != null) {
            propHolder.getPropValue().valueOf(databaseValues.get(propHolder.getName()));
        }
        addProperty(propHolder, restriction);
    }

    /**
     * Добавление опционального свойства в сущность.
     * @param name Идентификатор свойства.
     * @param value Начальное значение свойства.
     * @param require Признак того что свойство должно иметь значение.
     * селекторе.
     */
    public final void addExtraProp(String name, ISerializableType value, boolean require) {
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
                    MessageFormat.format("Model does not have property ''{0}''", propName)
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
                .filter(dynamicResolver.valueProviders::containsKey)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Обновление динамических свойств модели.
     * @param names Список свойст для обновления.
     */
    public final void updateDynamicProps(String... names) {
        dynamicResolver.updateDynamicProps(Arrays.stream(names)
                .filter((propName) -> dynamicProps.contains(propName) && dynamicResolver.valueProviders.containsKey(propName))
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Значение свойства должно быть уникальным среди сушностей у одного родителя
     * @param name Идентификатор свойства.
     */
    public final void setPropUnique(String name) {
        //TODO: У поля уже может быть назначена маска
        getProperty(name).getPropValue().setMask(new UniqueMask(name));
    }

    public final boolean isPropUnique(String name) {
        return
                getProperty(name).getPropValue().getMask() != null &&
                UniqueMask.class.isAssignableFrom(getProperty(name).getPropValue().getMask().getClass());
    }
    
    /**
     * Добавление слушателя события изменения значения свойства.
     */
    public final synchronized void addChangeListener(IPropertyChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     * Удаление слушателя события изменения значения свойства.
     */
    public final synchronized void removeChangeListener(IPropertyChangeListener listener) {
        changeListeners.remove(listener);
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

    /**
     * Получить значение свойства.
     * Если свойство изменено, но не сохранено, будет возвращено первоначальное значение.
     * @param name Имя свойства.
     */

    @Override
    public final Object getValue(String name) {
        if (undoRegistry.exists(name)) {
            return undoRegistry.previous(name);
        } else {
            return super.getValue(name);
        }
    }

    /**
     * Принудительно вычислить значение динамического свойства.
     * @param propName Имя свойства.
     */
    public Object calculateDynamicValue(final String propName) {
        return dynamicResolver.valueProviders.get(propName).get();
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

    /**
     * Проверка существования внешних ссылок на данную модель.
     * Внешняя ссылка - это поле типа EntityRef у другой сущности.
     */
    public final boolean existReferencies() {
        OrmContext.debug("Check references for entity ''{0}''", getQualifiedName());
        List<IConfigStoreService.ForeignLink> links = getConfigService().findReferencedEntries(entityClass, getID());
        links.removeIf(link -> {
            if (link.isIncoming) {
                return false;
            } else {
                EntityRef ref = EntityRef.build(link.entryClass, link.entryID);
                if (ref.getValue() != null && Entity.getDefinition(ref.getValue().getClass()).autoGenerated()) {
                    List<IConfigStoreService.ForeignLink> depends = getConfigService().findReferencedEntries(link.entryClass, link.entryID);
                    if (depends.isEmpty()) {
                        OrmContext.debug("Skip auto generated independent entity ''{0}''", ref.getValue().model.getQualifiedName());
                    }
                    return depends.isEmpty();
                } else {
                    return false;
                }
            }
        });
        if (links.isEmpty()) {
            return false;
        } else {
            OrmContext.debug(
                    "Entity ''{0}'' has references:\n{1}",
                    getQualifiedName(),
                    links.stream()
                        .map(link -> {
                            Entity referenced = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class).getEntity(link.entryClass, link.entryID);
                            return MessageFormat.format(
                                    "* {0}: {1}",
                                    link.isIncoming ? "incoming" : "outgoing",
                                    referenced != null ? referenced.getPathString() : link.entryPID
                            );
                        }).collect(Collectors.joining("\n"))
            );
            MessageBox.show(
                    MessageType.ERROR,
                    MessageFormat.format(Language.get("error@notdeleted"), getPID(false)).concat(
                            links.stream()
                                    .map(link -> {
                                        Entity referenced = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class).getEntity(link.entryClass, link.entryID);
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

    /**
     * Сохранение ряда полей модели.
     * @param showError Отображать диалог об ошибке.
     * @param propNames Массив свойств для сохранения.
     */
    public boolean commit(boolean showError, String... propNames) throws Exception {
        if (propNames != null && propNames.length > 0) {
            if (getID() == null) {
                throw new IllegalStateException(MessageFormat.format("Update failed: model {0} does not exist in database", getQualifiedName()));
            } else {
                List<String> updateProps = Arrays.asList(propNames);
                OrmContext.debug("Perform partial commit model {0} {1}", getQualifiedName(), updateProps);
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
                OrmContext.debug("Perform commit (create) model {0}", getQualifiedName());
                if (create(showError)) {
                    update(showError, getChanges() /* +SEQ */);
                }
            } else {
                if (maintenance(showError)) {
                    OrmContext.debug("Perform commit (update) model {0}", getQualifiedName());
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
            OrmContext.debug("Perform rollback model {0}", getQualifiedName());
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
                OrmContext.debug("Perform partial rollback model {0} {1}", getQualifiedName(), changes);
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
                getConfigService().initClassInstance(
                        entityClass, 
                        (String) getProperty(PID).getPropValue().getValue(), 
                        getPropDefinitions(this), 
                        ownerId
                ).forEach(this::setValue);
                return true;
            }
        } catch (Exception e) {
            OrmContext.error("Unable initialize model in database", e);
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
        Map<String, String> dbValues = getConfigService().readClassInstance(entityClass, getID());
        try {
            synchronized (this) {
                processAutoReferences(true, null);
                getConfigService().updateClassInstance(
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
                    if (bootProps.contains(propName)) {
                        Bootstrap.setProperty(
                                entityClass,
                                getPID(false),
                                propName,
                                getProperty(propName).getOwnPropValue().toString()
                        );
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
            OrmContext.error("Unable update model properties to database", e);
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
                    return Entity.getDefinition(entity.getClass()).autoGenerated() && (
                                ( create && entity.getID() == null) ||
                                (!create && entity.getID() != null && getConfigService().findReferencedEntries(entity.getClass(), entity.getID()).isEmpty())
                           );
                })
                .collect(Collectors.toSet());
        if (!autoReferences.isEmpty() && create) {
            for (Entity entity : autoReferences) {
                synchronized (entity) {
                    Entity owner = entity.getOwner();
                    boolean exists = getConfigService().isInstanceExists(entity.getClass(), entity.getPID(), owner == null ? null : owner.getID());
                    if (!exists) {
                        OrmContext.debug(
                                "Perform automatic entity creation: {0}",
                                entity.model.getQualifiedName()
                        );
                        getConfigService().initClassInstance(
                                entity.getClass(),
                                entity.getPID(),
                                getPropDefinitions(entity.model),
                                owner == null ? null : owner.getID()
                        ).forEach(entity.model::setValue);
                    } else {
                        OrmContext.debug(
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
                    boolean exists = getConfigService().isInstanceExists(entity.getClass(), entity.getPID(), owner == null ? null : owner.getID());
                    if (exists) {
                        OrmContext.debug(
                                "Perform automatic entities deletion: {0}",
                                entity.model.getQualifiedName()
                        );
                        entity.model.remove();
                    } else {
                        OrmContext.debug(
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
            dbValues = getConfigService().readClassInstance(entityClass, getPID(false), ownerId);
        } else {
            dbValues = getConfigService().readClassInstance(entityClass, getID());
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
            OrmContext.debug("Perform removal model {0}", getQualifiedName());
            try {
                synchronized (this) {
                    try {
                        for (IConfigStoreService.ForeignLink link : getConfigService().findReferencedEntries(entityClass, getID())) {
                            if (!link.isIncoming) {
                                EntityRef ref = EntityRef.build(link.entryClass, link.entryID);
                                if (ref.getValue() != null && Entity.getDefinition(ref.getValue().getClass()).autoGenerated() && getConfigService().findReferencedEntries(ref.getEntityClass(), ref.getId()).isEmpty()) {
                                    OrmContext.debug("Cascade removal dependent model {0}", ref.getValue().model.getQualifiedName());
                                    ref.getValue().model.remove(false);
                                }
                            }
                        }
                        getConfigService().removeClassInstance(entityClass, getID());
                        processAutoReferences(false, getProperties(Access.Any));
                    } catch (Exception e) {
                        OrmContext.error("Unable delete model from database", e);
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
        
        Map<String, String> dbValues = getConfigService().readClassInstance(entityClass, getID());
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
            OrmContext.debug("Perform maintenance class catalog {0}", entityClass.getSimpleName());
            try {
                getConfigService().maintainClassCatalog(entityClass, extraProps, absentProps);
                return true;
            } catch (Exception e) {
                OrmContext.error("Unable to maintain class catalog", e);
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
                                    .filter((basePropName) -> !isPropertyDynamic(basePropName))
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
                            List<String> baseDynProps = Arrays.stream(baseProps)
                                    .filter(EntityModel.this::isPropertyDynamic)
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
            List<String> filtered = names.stream()
                    .filter(propName -> ISerializableType.class.isAssignableFrom(getPropertyType(propName)))
                    .collect(Collectors.toList());

            if (filtered.size() == 1) {
                OrmContext.debug("Perform update dynamic property: {0}", filtered.get(0));
                getProperty(filtered.get(0)).setValue(dynamicResolver.valueProviders.get(filtered.get(0)).get());
            } else if (filtered.size() > 1) {
                Map<String, List<String>> updatePlan = buildUpdatePlan(filtered);
                
                List<String> independentProps = updatePlan.entrySet().stream()
                            .filter((entry) -> {
                                    return
                                        entry.getValue().size() == 1 && entry.getKey().equals(entry.getValue().get(0)) &&
                                                updatePlan.values().stream()
                                                        .filter((baseProps) -> baseProps != entry.getValue())
                                                        .noneMatch((baseProps) -> baseProps.contains(entry.getKey()));
                            })
                            .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                
                Map<String, List<String>> dependencies = updatePlan.entrySet().stream()
                            .filter((entry) -> {
                                    return !(entry.getValue().size() == 1 && entry.getKey().equals(entry.getValue().get(0)));
                            }).collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                            ));

                OrmContext.debug(
                        "Perform update dynamic properties: {0} by resolving plan\n"
                                + "Independent : {1}\n"
                                + "Dependencies: {2}",
                        filtered,
                        independentProps,
                        dependencies.entrySet().stream()
                                .map((entry) -> entry.getKey().concat("<=").concat(entry.getValue().toString()))
                                .collect(Collectors.joining(", "))
                );
                
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
                    OrmContext.debug(
                            "Perform forced update orphaned dynamic properties: {0}", 
                            dependencies.keySet()
                    );
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
                   getConfigService().readCatalogEntries(ownerId, entityClass).keySet().stream()
                         .noneMatch((ID) -> value.equals(getConfigService().readClassInstance(entityClass, ID).get(propName)) && !ID.equals(getID()));
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
