package codex.type;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.mask.IMask;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Тип-ссылка на сущность {@link Entity}.
 */
public class EntityRef implements IComplexType<Entity, IMask<Entity>> {
    
    private final static IConfigStoreService    CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);

    private Class             entityClass;
    private Integer           entityID;
    private Entity            entityInstance;
    private Predicate<Entity> entityFilter;
    
    /**
     * Констуктор типа.
     * @param entityClass Класс сущности для поиска допустимых значений.
     */
    public EntityRef(Class entityClass) {
        this(entityClass, null);
    }
    
    /**
     * Констуктор типа.
     * @param entityClass Класс сущности для поиска допустимых значений.
     * @param entityFilter Пользовательский фильтр допустимых значений.
     */
    public EntityRef(Class entityClass, Predicate<Entity> entityFilter) {
        setEntityClass(entityClass, entityFilter);
    }
    
    /**
     * Устанавливает класс сущности, используется редактором {@link EntityRefEditor}.
     * @param entityClass Класс сущности для поиска допустимых значений.
     * @param entityFilter Опционально - задать фильтр сущностей, если не 
     * требуется - указазать null.
     */
    public final void setEntityClass(Class entityClass, Predicate<Entity> entityFilter) {
        this.entityClass  = entityClass;
        this.entityFilter = entityFilter != null ? entityFilter : (entity) -> {
            return true;
        };
    }
    
    /**
     * Возвращает класс сущности, используется редактором {@link EntityRefEditor}.
     */
    public final Class getEntityClass() {
        return entityClass;
    }
    
    /**
     * Возвращает фильтр сущностей, используется редактором {@link EntityRefEditor}.
     */
    public final Predicate<Entity> getEntityFilter() {
        return entityFilter;
    }

    @Override
    public Entity getValue() {
        return entityInstance != null ? entityInstance : entityID != null ? createEntity() : null;
    }
    
    public Integer getId() {
        return entityID;
    }

    @Override
    public void setValue(Entity value) {
        entityInstance = value == null ? null : value;
        entityID = value == null ? null : value.model.getID();
    }
    
    @Override
    public boolean isEmpty() {
        return entityID == null && entityInstance == null;
    }
    
    public boolean isLoaded() {
        return entityInstance != null;
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return (PropertyHolder propHolder) -> {
            return new EntityRefEditor(propHolder);
        };
    }
    
    @Override
    public String toString() {
        return entityID != null ? 
                entityID.toString() : (
                    entityInstance != null ? entityInstance.model.getID().toString() : ""
                );
    }

    @Override
    public void valueOf(String value) {
        if (value != null && !value.isEmpty()) {
            entityID = Integer.valueOf(value);
        }
    }
    
    private Entity createEntity() {
        Map<String, String> databaseValues = CAS.readClassInstance(entityClass, entityID);
        EntityRef ownerRef = new EntityRef(CAS.getOwnerClass(entityClass));
        ownerRef.valueOf(databaseValues.get(EntityModel.OWN));
        entityInstance = Entity.newInstance(entityClass, ownerRef, databaseValues.get(EntityModel.PID));;
        return entityInstance;
    }
    
}
