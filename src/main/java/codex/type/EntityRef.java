package codex.type;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Тип-ссылка на сущность {@link Entity}.
 */
public class EntityRef implements IComplexType<Entity, IMask<Entity>> {
    
    /**
     * Результат проверки сущностей на предмет соответствия условию.
     */
    public enum Match {
        /**
         * Точное совпадение.
         */
        Exact, 
        /**
         * Частичное совпадение.
         */
        About, 
        /**
         * Не совпадает.
         */
        None, 
        /**
         * Проверка не может быть выполнена.
         */
        Unknown
    }
    
    private final static IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);

    private Class<? extends Entity> entityClass;
    private Entity            entityInstance;
    private Predicate<Entity> entityFilter;
    private Function<Entity, Match> entityMatcher;
    
    /**
     * Констуктор типа.
     * @param entityClass Класс сущности для поиска допустимых значений.
     */
    public EntityRef(Class<? extends Entity> entityClass) {
        this(entityClass, null);
    }
    
    /**
     * Констуктор типа.
     * @param entityClass Класс сущности для поиска допустимых значений.
     * @param entityFilter Пользовательский фильтр допустимых значений.
     */
    public EntityRef(Class<? extends Entity> entityClass, Predicate<Entity> entityFilter) {
        this(entityClass, entityFilter, null);
    }
    
    /**
     * Констуктор типа.
     * @param entityClass Класс сущности для поиска допустимых значений.
     * @param entityFilter Пользовательский фильтр допустимых значений.
     * @param entityMatcher Условие соответствия сущности. Используется для 
     * подсветки цветом в выпадающем списке {@link EntityRefEditor}.
     */
    public EntityRef(Class<? extends Entity> entityClass, Predicate<Entity> entityFilter, Function<Entity, Match> entityMatcher) {
        setEntityClass(entityClass, entityFilter, entityMatcher);
    }
    
    /**
     * Устанавливает класс сущности, используется редактором {@link EntityRefEditor}.
     * @param entityClass Класс сущности для поиска допустимых значений.
     * @param entityFilter Опционально - задать фильтр сущностей, если не 
     * требуется - указазать null.
     * @param entityMatcher Условие соответствия сущности. Используется для 
     * подсветки цветом в выпадающем списке {@link EntityRefEditor}.
     */
    private void setEntityClass(Class<? extends Entity> entityClass, Predicate<Entity> entityFilter, Function<Entity, Match> entityMatcher) {
        this.entityClass  = entityClass;
        this.entityFilter = entityFilter != null ? entityFilter : (entity) -> true;
        this.entityMatcher = entityMatcher != null ? entityMatcher : (entity) -> Match.Unknown;
    }
    
    /**
     * Возвращает класс сущности, используется редактором {@link EntityRefEditor}.
     */
    public final Class<? extends Entity> getEntityClass() {
        return entityClass;
    }
    
    /**
     * Возвращает фильтр сущностей, используется редактором {@link EntityRefEditor}.
     */
    public final Predicate<Entity> getEntityFilter() {
        return entityFilter;
    }
    
    /**
     * Возвращает условие проверки сущностей.
     */
    public final Function<Entity, Match> getEntityMatcher() {
        return entityMatcher;
    }

    @Override
    public Entity getValue() {
        return entityInstance;
    }
    
    /**
     * Возвращает идентификатор сущности.
     */
    public Integer getId() {
        return getValue().getID();
    }

    @Override
    public void setValue(Entity value) {
        entityInstance = value;
    }
    
    @Override
    public boolean isEmpty() {
        return entityInstance == null;
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return (PropertyHolder propHolder) -> new EntityRefEditor(propHolder);
    }
    
    @Override
    public String toString() {
        return entityInstance != null && entityInstance.getID() != null ? entityInstance.getID().toString() : "";
    }

    @Override
    public void valueOf(String value) {
        if (value != null && !value.isEmpty()) {
            entityInstance = build(entityClass, value).getValue();
        } else {
            entityInstance = null;
        }
    }
    
    @Override
    public String getQualifiedValue(Entity val) {
        return val == null ? "<NULL>" : val.model.getQualifiedName();
    }
    
    /**
     * Построение ссылки на сущность.
     * @param entityClass Класс сущности.
     * @param entityId Идентификатор сущности в строковом виде.
     */
    public static EntityRef build(Class<? extends Entity> entityClass, String entityId) {
        return build(entityClass, entityId == null || entityId.isEmpty() ? null : Integer.valueOf(entityId));
    }
    
    /**
     * Построение ссылки на сущность.
     * @param entityClass Класс сущности.
     * @param entityId Идентификатор сущности.
     */
    public static EntityRef build(Class<? extends Entity> entityClass, Integer entityId) {
        if (entityClass != null && entityId != null && CAS.isInstanceExists(entityClass, entityId)) {
            Map<String, String> dbValues = CAS.readClassInstance(entityClass, entityId);
            
            EntityRef ownerRef = null;
            try {
                ownerRef = build(CAS.getOwnerClass(entityClass), dbValues.get("OWN"));
            } catch (Exception e) {
                //
            }
            return Entity.newInstance(entityClass, ownerRef, dbValues.get("PID")).toRef();
        }
        return null;
    }
    
}
