package codex.type;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import java.util.function.Predicate;

/**
 * Тип-ссылка на сущность {@link Entity}.
 */
public class EntityRef implements IComplexType<Entity> {
    
    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private Class             entityClass;
    private Predicate<Entity> entityFilter;
    private Entity value;
    
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
        return value;
    }

    @Override
    public void setValue(Entity value) {
        this.value = value;
    }
    
    @Override
    public boolean isEmpty() {
        return getValue() == null;
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return (PropertyHolder propHolder) -> {
            return new EntityRefEditor(propHolder);
        };
    }
    
    @Override
    public String toString() {
        return isEmpty() ? "" : value.model.getID().toString();
    }

    @Override
    public void valueOf(String value) {
        if (value != null && !value.isEmpty()) {
            String PID = STORE.readClassInstance(entityClass, Integer.valueOf(value)).get(EntityModel.PID);
            if (PID != null) {
                Entity entity = Entity.newInstance(entityClass, PID);
                setValue(entity);
            }
        }
    }
    
}
