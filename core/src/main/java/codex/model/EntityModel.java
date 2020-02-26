package codex.model;

import codex.command.EditorCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.IConfigStoreService;
import codex.context.IContext;
import codex.editor.IEditor;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Реализация модели сущности.
 */
public class EntityModel extends AbstractModel implements IPropertyChangeListener {

    public  final static String ID   = "ID";   // Primary unique identifier
    public  final static String OWN  = "OWN";  // Reference to owner entity
    public  final static String SEQ  = "SEQ";  // Order sequence number
    public  final static String PID  = "PID";  // Title or name
    public  final static String OVR  = "OVR";  // List of overridden values
    public  final static String THIS = "THIS"; // Reference to the entity object
    
    private final static Boolean      DEV_MODE  = "1".equals(java.lang.System.getProperty("showSysProps"));
    public  final static List<String> SYSPROPS  = Arrays.asList(ID, OWN, SEQ, PID, OVR);

    private final DynamicResolver               dynamicResolver  = new DynamicResolver();
    private final ReferenceTracker              referenceTracker = new ReferenceTracker();
    private final Map<String, String>           databaseValues;
    private final Map<String, Object>           initialValues   = new HashMap<>();
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();

    private final Class<? extends Entity>       entityClass, tableClass;
    private final List<String>                  dynamicProps    = new LinkedList<>();
    private final UndoRegistry                  undoRegistry    = new UndoRegistry();
    private final List<IModelListener>          modelListeners  = new LinkedList<>();

    // Контексты
    @LoggingSource()
    @IContext.Definition(id = "OEM", name = "Object entity model", icon = "/images/model.png")
    static class OrmContext implements IContext {}
    
    EntityModel(EntityRef owner, Class<? extends Entity> entityClass, String PID) {
        this.entityClass = entityClass;
        this.tableClass  = PolyMorph.class.isAssignableFrom(entityClass) ? PolyMorph.getPolymorphClass(entityClass) : entityClass;

        Integer ownerId = owner == null ? null : owner.getId();
        this.databaseValues = getConfigService().readClassInstance(tableClass, PID, ownerId);
        if (PolyMorph.class.isAssignableFrom(entityClass)) {
            databaseValues.putAll(PolyMorph.parseParameters(databaseValues.get(PolyMorph.PROP_IMPL_PARAM)));
        }
        
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
                !ICatalog.class.isAssignableFrom(entityClass),
                DEV_MODE ? Access.Select : Access.Any
        );
        addUserProp(EntityModel.PID, new Str(PID),  
                true,
                ICatalog.class.isAssignableFrom(entityClass) ? Access.Any : Access.Select
        );

        if (owner != null) {
            // Подмена класса полиморфных сущностей чтобы:
            // * при создании записи в БД создалась ссылка на нужную таблицу
            // * при загрузке запись из БД корректно прогрузилась в свойство
            Class<? extends Entity> tableClass = owner.getValue() instanceof PolyMorph ?
                    PolyMorph.getPolymorphClass(owner.getValue().getClass()) : owner.getValue().getClass();
            EntityRef ownerRef = new EntityRef<>(tableClass.asSubclass(Entity.class));
            //noinspection unchecked
            ownerRef.setValue(owner.getValue());
            addUserProp(EntityModel.OWN, ownerRef, false, DEV_MODE ? null : Access.Any);
        } else {
            addUserProp(EntityModel.OWN, new EntityRef<>(null), false, DEV_MODE ? null : Access.Any);
        }
        addUserProp(
                EntityModel.OVR, 
                new ArrStr(databaseValues.get(OVR) != null ? ArrStr.parse(databaseValues.get(OVR)) : new LinkedList<>()),
                false, 
                DEV_MODE ? Access.Select : Access.Any
        );
        
        addPropertyGroup("System properties", ID, SEQ, OWN, OVR);
        setPropUnique(EntityModel.PID);
    }

    Class<? extends Entity> getEntityClass() {
        return entityClass;
    }

    Class<? extends Entity> getTableClass() {
        return tableClass;
    }

    IConfigStoreService getConfigService() {
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
    
    @SuppressWarnings("unchecked")
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
        addProperty(propHolder, restriction); //Сначала нужно создать свойство и заполнить initialValues
        if (databaseValues != null && databaseValues.get(name) != null) {
            propHolder.getPropValue().valueOf(databaseValues.get(name));
        }
        referenceTracker.trackProperty(propHolder);
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
        addProperty(propHolder, restriction); //Сначала нужно создать свойство и заполнить initialValues
        if (databaseValues != null && databaseValues.get(propHolder.getName()) != null) {
            propHolder.getPropValue().valueOf(databaseValues.get(propHolder.getName()));
        }
        referenceTracker.trackProperty(propHolder);
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
        referenceTracker.trackProperty(getProperty(name));
    }

    public final boolean hasExtraProps() {
        return restrictions.values().contains(Access.Extra);
    }

    public final boolean isPropertyExtra(String propName) {
        if (!hasProperty(propName)) {
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

    @SuppressWarnings("unchecked")
    public final void addDynamicProp(String name, String title, String desc, IComplexType value, Access restriction, Supplier valueProvider, String... baseProps) {
        if (valueProvider != null) {
            addProperty(
                    dynamicResolver.newProperty(name, title, desc, value, valueProvider, baseProps),
                    restriction
            );
        } else {
            addProperty(new PropertyHolder<>(name, title, desc, value, false), restriction);
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
    @SuppressWarnings("unchecked")
    public final void setPropUnique(String name) {
        //TODO: У поля уже может быть назначена маска
        //Можно подумать над автогенерацией маски для ключевых полей (новое поле в PropertyDefinition)
        getProperty(name).getPropValue().setMask(new UniqueMask(name));
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
        new LinkedList<>(changeListeners).forEach((listener) -> listener.propertyChange(name, oldValue, newValue));
        new LinkedList<>(modelListeners).forEach((listener)  -> listener.modelChanged(this, getChanges()));
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
     */
    @SuppressWarnings("unchecked")
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
    @SuppressWarnings("unchecked")
    public Class<? extends IComplexType> getPropertyType(String name) {
        return getProperty(name).getType();
    }
    
    /**
     * Получить наименование свойства.
     */
    public final String getPropertyTitle(String propName) {
        return getProperty(propName).getTitle();
    }
    
    public final boolean isPropertyDynamic(String propName) {
        if (!hasProperty(propName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Model does not have property ''{0}''", propName)
            );
        }
        return dynamicProps.contains(propName);
    }

    public final boolean isStateProperty(String propName) {
        return getStateProps().contains(propName);
    }

    private List<String> getStateProps() {
        List<String> props = new LinkedList<>();

        Class<?> nextClass = entityClass;
        while (Entity.class.isAssignableFrom(nextClass)) {
            Stream.of(nextClass.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(PropertyDefinition.class))
                    .filter(field -> field.getAnnotation(PropertyDefinition.class).state())
                    .forEach(field -> {
                        try {
                            field.setAccessible(true);
                            props.add((String) field.get(this));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
            nextClass = nextClass.getSuperclass();
        }
        return props;
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

    Collection<Reference> getReferences() {
        return referenceTracker.getReferences().stream()
                .sorted(Comparator.comparing(o -> o.model.entityClass.getTypeName()))
                .collect(Collectors.toList());
    }

    /**
     * Сохранение изменений модели.
     */
    public final void commit(boolean showError) throws Exception {
        if (!getChanges().isEmpty()) {
            Logger.getContextLogger(OrmContext.class).debug("Perform full commit model {0} {1}", getQualifiedName(), getChanges());
            commit(showError, getChanges());
        }
    }

    /**
     * Сохранение ряда полей модели.
     * @param showError Отображать диалог об ошибке.
     * @param propNames Массив свойств для сохранения.
     */
    public final void commit(boolean showError, String... propNames) throws Exception {
        if (propNames != null && propNames.length > 0) {
            List<String> updateProps = Arrays.stream(propNames).collect(Collectors.toList());
            updateProps.removeIf(propName -> !hasProperty(propName));
            if (PolyMorph.class.isAssignableFrom(entityClass)) {
                updateProps.add(PolyMorph.PROP_IMPL_PARAM);
            }
            if (!updateProps.isEmpty()) {
                Logger.getContextLogger(OrmContext.class).debug("Perform partial commit model {0} {1}", getQualifiedName(), updateProps);
                commit(showError, updateProps);
            }
        }
    }

    private void commit(boolean showError, List<String> propNames) throws Exception {
        if (!propNames.isEmpty()) {
            if (getID() == null) {
                Logger.getContextLogger(OrmContext.class).debug("Insert model to database {0}", getQualifiedName());
                if (!create(showError)) {
                    return;
                }
            }
            if (maintenance(showError)) {
                Logger.getContextLogger(OrmContext.class).debug("Update model in database {0}", getQualifiedName());
                update(showError, propNames);
            }
        }
    }
    
    /**
     * Откат изменений модели.
     */
    @SuppressWarnings("unchecked")
    public final void rollback() {
        List<String> changes = getChanges();
        if (!changes.isEmpty()) {
            Logger.getContextLogger(OrmContext.class).debug("Perform rollback model {0}", getQualifiedName());
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
    @SuppressWarnings("unchecked")
    public final void rollback(String... propNames) {
        if (propNames != null && propNames.length > 0) {
            List<String> changes = getChanges().stream()
                    .filter(propName -> Arrays.asList(propNames).contains(propName))
                    .collect(Collectors.toList());
            if (!changes.isEmpty()) {
                Logger.getContextLogger(OrmContext.class).debug("Perform partial rollback model {0} {1}", getQualifiedName(), changes);
                changes.forEach((name) -> getProperty(name).setValue(undoRegistry.previous(name)));
                new LinkedList<>(modelListeners).forEach((listener) -> listener.modelRestored(this, changes));
            }
        }
    }

    private static Map<String, IComplexType> getPropDefinitions(EntityModel model) {
        Map<String, IComplexType> propDefinitions = new LinkedHashMap<>();
        Predicate<String> modelFilter = (propName) -> !model.dynamicProps.contains(propName) || EntityModel.ID.equals(propName);
        Predicate<String> morphFilter = PolyMorph.class.isAssignableFrom(model.entityClass) ?
                (propName) -> !PolyMorph.getExternalProperties(model).contains(propName) :
                (propName) -> true;

        model.getProperties(Access.Any)
            .stream()
            .filter(modelFilter)
            .filter(morphFilter)
            .forEach((propName) -> propDefinitions.put(propName, model.getProperty(propName).getPropValue()));
        return propDefinitions;
    }
    
    @SuppressWarnings("unchecked")
    public boolean create(boolean showError) throws Exception {
        Integer ownerId = getOwner() == null ? null : getOwner().getID();
        try {
            synchronized (this) {
                getConfigService().initClassInstance(
                        tableClass,
                        (String) getProperty(PID).getPropValue().getValue(),
                        getPropDefinitions(this),
                        ownerId
                ).forEach((propName, generatedVal) -> getProperty(propName).getPropValue().setValue(generatedVal));
                return true;
            }
        } catch (Exception e) {
            Logger.getContextLogger(OrmContext.class).error("Unable initialize model in database", e);
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

    @SuppressWarnings("unchecked")
    protected boolean update(boolean showError, List<String> changes) throws Exception {
        final Map<String, String> dbValues = getConfigService().readClassInstance(tableClass, getID());
        try {
            synchronized (this) {
                processAutoReferences(true, null);

                Predicate<String> equalFilter = (propName) -> !Objects.equals(getProperty(propName).getOwnPropValue().toString(), dbValues.get(propName));
                Predicate<String> morphFilter = PolyMorph.class.isAssignableFrom(entityClass) ?
                        (propName) -> !PolyMorph.getExternalProperties(this).contains(propName) :
                        (propName) -> true;

                getConfigService().updateClassInstance(
                        tableClass,
                        getID(),
                        changes.stream()
                            .filter(equalFilter)
                            .filter(morphFilter)
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
            new LinkedList<>(modelListeners).forEach((listener) -> listener.modelSaved(this, new LinkedList<>(changes)));
            changes.forEach((propName) -> {
                List<EditorCommand> commands = getEditor(propName).getCommands();
                commands.forEach(EditorCommand::activate);
            });
            return true;
        } catch (Exception e) {
            Logger.getContextLogger(OrmContext.class).error("Unable update model properties to database", e);
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
    
    private void processAutoReferences(boolean create, List<String> propList) throws Exception {
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
                        Logger.getContextLogger(OrmContext.class).debug(
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
                        Logger.getContextLogger(OrmContext.class).debug(
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
                        Logger.getContextLogger(OrmContext.class).debug(
                                "Perform automatic entities deletion: {0}",
                                entity.model.getQualifiedName()
                        );
                        entity.remove(false, false);
                    } else {
                        Logger.getContextLogger(OrmContext.class).debug(
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
    @SuppressWarnings("unchecked")
    public void read() {
        Map<String, String> dbValues;
        Integer ownerId = getOwner() == null ? null : getOwner().getID();
        if (getID() == null) {
            dbValues = getConfigService().readClassInstance(tableClass, getPID(false), ownerId);
        } else {
            dbValues = getConfigService().readClassInstance(tableClass, getID());
        }
        List<String> userProps = getProperties(Access.Any).stream()
                .filter((propName) -> !isPropertyDynamic(propName) || SYSPROPS.contains(propName))
                .collect(Collectors.toList());
        
        userProps.forEach((propName) -> {
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
        Logger.getContextLogger(OrmContext.class).debug("Perform removal model {0}", getQualifiedName());
        try {
            synchronized (this) {
                try {
                    for (IConfigStoreService.ForeignLink link : getConfigService().findReferencedEntries(tableClass, getID())) {
                        if (!link.isIncoming) {
                            EntityRef ref = EntityRef.build(link.entryClass, link.entryID);
                            if (ref.getValue() != null && Entity.getDefinition(ref.getValue().getClass()).autoGenerated() && getConfigService().findReferencedEntries(ref.getEntityClass(), ref.getId()).isEmpty()) {
                                Logger.getContextLogger(OrmContext.class).debug("Cascade removal dependent model {0}", ref.getValue().model.getQualifiedName());
                                ref.getValue().model.remove(false);
                            }
                        }
                    }
                    getConfigService().removeClassInstance(tableClass, getID());
                    processAutoReferences(false, getProperties(Access.Any));
                } catch (Exception e) {
                    Logger.getContextLogger(OrmContext.class).error("Unable delete model from database", e);
                    throw e;
                } finally {
                    if (readAfter) {
                        read();
                    }
                }
            }
            new LinkedList<>(modelListeners).forEach((listener) -> listener.modelDeleted(this));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean maintenance(boolean showError) {
        Predicate<String> modelFilter = (propName) -> !isPropertyDynamic(propName);
        Predicate<String> morphFilter = PolyMorph.class.isAssignableFrom(entityClass) ?
                (propName) -> !PolyMorph.getExternalProperties(this).contains(propName) :
                (propName) -> true;

        List<String> propList = getProperties(Access.Any).stream()
                .filter(modelFilter)
                .filter(morphFilter)
                .collect(Collectors.toList());
        Map<String, String> dbValues = getConfigService().readClassInstance(tableClass, getID());

        List<String> extraProps = dbValues.keySet().stream()
                .filter((propName) -> !propList.contains(propName) && !EntityModel.SYSPROPS.contains(propName))
                .collect(Collectors.toList());
        
        Map<String, IComplexType> absentProps = propList.stream()
                .filter((propName) -> !dbValues.containsKey(propName))
                .collect(Collectors.toMap(
                        propName -> propName, 
                        propName -> getProperty(propName).getPropValue()
                ));

        if (!extraProps.isEmpty() || !absentProps.isEmpty()) {
            Logger.getContextLogger(OrmContext.class).debug("Perform maintenance class catalog {0}", tableClass.getSimpleName());
            try {
                getConfigService().maintainClassCatalog(tableClass, extraProps, absentProps);
                return true;
            } catch (Exception e) {
                Logger.getContextLogger(OrmContext.class).error("Unable to maintain class catalog", e);
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
                    editor.setEditable(!ICatalog.class.isAssignableFrom(entityClass));
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


    private final class DynamicResolver {

        final Map<String, List<String>>   resolveMap      = new HashMap<>();
        final Map<String, Supplier>       valueProviders  = new HashMap<>();
        final Map<String, PropertyHolder> propertyHolders = new HashMap<>();

        PropertyHolder newProperty(String name, IComplexType value, Supplier valueProvider, String... baseProps) {
            return newProperty(
                    name,
                    EntityModel.SYSPROPS.contains(name) ?
                            Language.get(EntityModel.class, name+PropertyHolder.PROP_NAME_SUFFIX) :
                            Language.lookup(name+PropertyHolder.PROP_NAME_SUFFIX),
                    EntityModel.SYSPROPS.contains(name) ?
                            Language.get(EntityModel.class, name+PropertyHolder.PROP_DESC_SUFFIX) :
                            Language.lookup(name+PropertyHolder.PROP_DESC_SUFFIX),
                    value,
                    valueProvider,
                    baseProps
            );
        }

        @SuppressWarnings("unchecked")
        PropertyHolder newProperty(String name, String title, String desc, IComplexType value, Supplier valueProvider, String... baseProps) {
            valueProviders.put(name, valueProvider);

            propertyHolders.put(name, new PropertyHolder(name, title, desc, value, false) {
                private boolean initiated = false;

                @Override
                public synchronized IComplexType getPropValue() {
                    if (!initiated) {
                        initiated = true;
                        value.setValue(valueProvider.get());
                        if (baseProps != null) {
                            // User props
                            List<String> baseUserProps = Arrays.stream(baseProps)
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
                            
                            List<String> baseRefProps = Arrays.stream(baseProps)
                                    .filter((basePropName) -> getPropertyType(basePropName) == EntityRef.class)
                                    .peek((baseRefPropName) -> {
                                        Entity baseEntity = (Entity) getValue(baseRefPropName);
                                        if (baseEntity != null) {
                                            baseEntity.model.addModelListener(refModelListener);
                                            baseEntity.model.properties.values().stream()
                                                    .filter((propHolder) -> baseEntity.model.dynamicProps.contains(propHolder.getName()))
                                                    .forEach((propHolder) -> propHolder.addChangeListener(refPropListener));
                                        }
                                    })
                                    .collect(Collectors.toList());
                            if (!baseRefProps.isEmpty()) {
                                EntityModel.this.addChangeListener((name, oldValue, newValue) -> {
                                    if (baseRefProps.contains(name)) {
                                        if (oldValue != null) {
                                            ((Entity) oldValue).model.removeModelListener(refModelListener);
                                            ((Entity) oldValue).model.properties.values().stream()
                                                    .filter((propHolder) -> ((Entity) oldValue).model.dynamicProps.contains(propHolder.getName()))
                                                    .forEach((propHolder) -> propHolder.removeChangeListener(refPropListener));
                                        }
                                        if (newValue != null) {
                                            ((Entity) newValue).model.addModelListener(refModelListener);
                                            ((Entity) newValue).model.properties.values().stream()
                                                    .filter((propHolder) -> ((Entity) newValue).model.dynamicProps.contains(propHolder.getName()))
                                                    .forEach((propHolder) -> propHolder.addChangeListener(refPropListener));
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
        
        @SuppressWarnings("unchecked")
        void updateDynamicProps(List<String> names) {
            List<String> filtered = names.stream()
                    .filter(propName -> ISerializableType.class.isAssignableFrom(getPropertyType(propName)))
                    .collect(Collectors.toList());

            if (filtered.size() == 1) {
                Logger.getContextLogger(OrmContext.class).debug("Perform update ''{0}'' dynamic property: {1}", getQualifiedName(), filtered.get(0));
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

                Logger.getContextLogger(OrmContext.class).debug(
                        "Perform update ''{0}'' dynamic properties: {1} by resolving plan\n"
                                + "Independent : {2}\n"
                                + "Dependencies: {3}",
                                getQualifiedName(),
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

                newValues.forEach((key, value) -> {
                    if (getValue(key) != value) {
                        setValue(key, value);
                        dependencies.values().removeIf((baseProps) -> baseProps.contains(key));
                    }
                });
                if (!dependencies.isEmpty()) {
                    Logger.getContextLogger(OrmContext.class).debug(
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
            names.forEach((propName) -> plan.put(propName, getBaseProps(propName)));
            return plan;
        }
        
        private List<String> getBaseProps(String propName) {
            List<String> chain = new LinkedList<>();
            if (!resolveMap.containsKey(propName)) {
                chain.add(propName);
            } else {
                resolveMap.get(propName).forEach((basePropName) -> chain.addAll(getBaseProps(basePropName)));
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
                   getConfigService().readCatalogEntries(ownerId, entityClass).stream().noneMatch(entityRef ->
                           !entityRef.getId().equals(getID()) &&
                           Objects.equals(entityRef.getValue().model.getProperty(propName).getOwnPropValue().toString(), value)
                   );
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


    private enum RefStatus {
        Temporal, Permanent
    }


    class Reference implements IPropertyChangeListener, IModelListener {
        final EntityModel  model;
        final String       property;
        final boolean      incoming;
        volatile RefStatus status = RefStatus.Temporal;

        Reference(EntityModel model, String property) {
            this.model = model;
            this.property = property;
            this.incoming = !property.equals(EntityModel.OWN);
            if (model.getID() != null) {
                setStatus(RefStatus.Permanent);
            } else {
                model.addChangeListener(Reference.this);
            }
            model.addModelListener(Reference.this);
        }

        Entity getEntity() {
            return model.getID() == null ? null : EntityRef.build(model.tableClass, model.getID()).getValue();
        }

        void unlink(Entity entity) {
            if (incoming) {
                model.referenceTracker.getHandler(property).clearValue(entity);
            }
        }

        void discard() {
            model.removeChangeListener(Reference.this);
            model.removeModelListener(Reference.this);
            Logger.getContextLogger(OrmContext.class).debug(
                    "Unregistered {0} reference {1} <- {2}",
                    incoming ? "incoming" : "child",
                    getQualifiedName(),
                    Reference.this
            );
        }

        synchronized void setStatus(RefStatus status) {
            this.status = status;
            if (status.equals(RefStatus.Permanent)) {
                Logger.getContextLogger(OrmContext.class).debug(
                        "Registered {0} reference {1} <- {2}",
                        incoming ? "incoming" : "child",
                        getQualifiedName(),
                        Reference.this
                );
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Reference reference = (Reference) o;
            return Objects.equals(model.getQualifiedName(), reference.model.getQualifiedName()) &&
                   Objects.equals(property, reference.property);
        }

        @Override
        public int hashCode() {
            return Objects.hash(model.getQualifiedName(), property);
        }

        @Override
        public String toString() {
            return MessageFormat.format("{0}@{1}", model.getQualifiedName(), property);
        }

        @Override
        public void propertyChange(String name, Object oldValue, Object newValue) {
            if (name.equals(EntityModel.ID)) {
                setStatus(newValue != null ? RefStatus.Permanent: RefStatus.Temporal);
            }
        }

        @Override
        public void modelDeleted(EntityModel deleted) {
            referenceTracker.unregisterReference(this.model, this.property);
        }
    }


    interface ITrackHandler<T extends ISerializableType<V, ? extends IMask<V>>, V> {
        Collection<Entity> getValue(V value);
        void clearValue(Entity value);
    }


    private abstract class AbstractTrackHandler<T extends ISerializableType<V, ? extends IMask<V>>, V>
            implements ITrackHandler<T, V>, IPropertyChangeListener
    {
        protected final PropertyHolder<T, V> propHolder;
        AbstractTrackHandler(PropertyHolder<T, V> propHolder) {
            this.propHolder = propHolder;
            this.propHolder.addChangeListener(this);
            if (getValue(propHolder.getPropValue().getValue()) != null) {
                getValue(propHolder.getPropValue().getValue()).forEach(entity -> entity.model.referenceTracker.registerReference(
                        EntityModel.this, propHolder.getName()
                ));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public final void propertyChange(String name, Object oldValue, Object newValue) {
            Collection<Entity> oldEntities = oldValue != null ? getValue((V) oldValue) : Collections.emptyList();
            Collection<Entity> newEntities = newValue != null ? getValue((V) newValue) : Collections.emptyList();
            oldEntities.forEach(entity -> {
                if (!newEntities.contains(entity)) {
                    entity.model.referenceTracker.unregisterReference(EntityModel.this, name);
                }
            });
            newEntities.forEach(entity -> {
                if (!oldEntities.contains(entity)) {
                    entity.model.referenceTracker.registerReference(EntityModel.this, name);
                }
            });
        }
    }


    private class DefaultRefHandler extends AbstractTrackHandler<EntityRef<Entity>, Entity> {
        DefaultRefHandler(PropertyHolder<EntityRef<Entity>, Entity> propertyHolder) {
            super(propertyHolder);
        }

        @Override
        public Collection<Entity> getValue(Entity value) {
            return value == null ? null : Collections.singleton(value);
        }

        @Override
        public void clearValue(Entity value) {
            EntityModel.this.setValue(propHolder.getName(), null);
            try {
                EntityModel.this.commit(true, propHolder.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class MapKeyRefHandler extends AbstractTrackHandler<codex.type.Map<Entity, Object>, Map<Entity, Object>> {

        MapKeyRefHandler(PropertyHolder<codex.type.Map<Entity, Object>, Map<Entity, Object>> propHolder) {
            super(propHolder);
        }

        @Override
        public Collection<Entity> getValue(Map<Entity, Object> value) {
            return value.keySet();
        }

        @Override
        public void clearValue(Entity value) {
            Map<Entity, Object> propValue = propHolder.getPropValue().getValue();
            propValue.remove(value);
            EntityModel.this.setValue(propHolder.getName(), propValue);
            try {
                EntityModel.this.commit(true, propHolder.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private class MapValRefHandler extends AbstractTrackHandler<codex.type.Map<Object, Entity>, Map<Object, Entity>> {

        MapValRefHandler(PropertyHolder<codex.type.Map<Object, Entity>, Map<Object, Entity>> propHolder) {
            super(propHolder);
        }

        @Override
        public Collection<Entity> getValue(Map<Object, Entity> value) {
            return value.values();
        }

        @Override
        public void clearValue(Entity value) {
            Map<Object, Entity> propValue = propHolder.getPropValue().getValue();
            propValue.entrySet().removeIf(entry -> entry.getValue().equals(value));
            EntityModel.this.setValue(propHolder.getName(), propValue);
            try {
                EntityModel.this.commit(true, propHolder.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private class ReferenceTracker {
        final private Map<String, ITrackHandler> handlers = new HashMap<>();
        final private List<Reference> references = new ArrayList<>();

        @SuppressWarnings("unchecked")
        void trackProperty(PropertyHolder propHolder) {
            if (propHolder.getType().equals(EntityRef.class)) {
                handlers.put(
                        propHolder.getName(),
                        new DefaultRefHandler((PropertyHolder<EntityRef<Entity>, Entity>) propHolder)
                );
            }
            if (propHolder.getType().equals(codex.type.Map.class)) {
                Class<? extends ISerializableType> keyClass = ((codex.type.Map) propHolder.getPropValue()).getKeyClass();
                Class<? extends ISerializableType> valClass = ((codex.type.Map) propHolder.getPropValue()).getValClass();
                if (keyClass.equals(EntityRef.class)) {
                    handlers.put(
                            propHolder.getName(),
                            new MapKeyRefHandler((PropertyHolder<codex.type.Map<Entity, Object>, Map<Entity, Object>>) propHolder)
                    );
                }
                if (valClass.equals(EntityRef.class)) {
                    handlers.put(
                            propHolder.getName(),
                            new MapValRefHandler((PropertyHolder<codex.type.Map<Object, Entity>, Map<Object, Entity>>) propHolder)
                    );
                }
            }
        }

        ITrackHandler getHandler(String propName) {
            return handlers.get(propName);
        }

        void registerReference(EntityModel model, String property) {
            synchronized (references) {
                references.add(new Reference(model, property));
            }
        }

        void unregisterReference(EntityModel model, String property) {
            findReference(model, property)
                    .ifPresent(reference -> {
                        reference.discard();
                        references.remove(reference);
                    });
        }

        Optional<Reference> findReference(EntityModel model, String property) {
            synchronized (references) {
                return references.stream()
                        .filter(reference -> Objects.hash(model.getQualifiedName(), property) == reference.hashCode())
                        .findFirst();
            }
        }

        Collection<Reference> getReferences() {
            synchronized (references) {
                return references.stream()
                       .filter(reference -> reference.status.equals(RefStatus.Permanent))
                       .collect(Collectors.toList());
            }
        }

    }
}
