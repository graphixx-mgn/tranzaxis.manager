package codex.type;

import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.mask.IMask;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import java.util.function.Predicate;

/**
 * Тип-ссылка на сущность {@link Entity}.
 */
public class EntityRef implements IComplexType<Entity, IMask<Entity>> {

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
     * Возвращает класс сущности, используется редактором {@link EntityRefEditor}.
     */
    public final Class getEntityClass() {
        return entityClass;
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
     * Возвращает фильтр сущностей, используется редактором {@link EntityRefEditor}.
     */
    public final Predicate<Entity> getEntityFilter() {
        return entityFilter;
    }

    @Override
    public Entity getValue() {
        return entityInstance != null ? entityInstance : entityID != null ? findEntity() : null;
    }
    
    private Entity findEntity() {
        entityInstance = (
                (IExplorerAccessService) ServiceRegistry
                        .getInstance()
                        .lookupService(ExplorerAccessService.class)
        ).getEntity(entityClass, entityID);
        return entityInstance;
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
    
    @Override
    public IEditorFactory editorFactory() {
        return (PropertyHolder propHolder) -> {
            return new EntityRefEditor(propHolder);
        };
    }
    
    @Override
    public String toString() {
        return isEmpty() ? "" : entityID.toString();
    }

    @Override
    public void valueOf(String value) {
        if (value != null && !value.isEmpty()) {
            entityID = Integer.valueOf(value);
        }
    }
    
}
