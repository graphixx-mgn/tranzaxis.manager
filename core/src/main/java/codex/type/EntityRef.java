package codex.type;

import codex.config.IConfigStoreService;
import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.mask.IRefMask;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import java.util.Map;

/**
 * Тип-ссылка на сущность {@link Entity}.
 */
public class EntityRef<E extends Entity> implements ISerializableType<E, IRefMask<E>>, IParametrized {

    private final static IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);

    private Class<E> entityClass;
    private E entityInstance;
    private IRefMask<E> mask = value -> true;
    
    /**
     * Констуктор типа.
     * @param entityClass Класс сущности для поиска допустимых значений.
     */
    public EntityRef(Class<E> entityClass) {
        setEntityClass(entityClass);
    }

    @Override
    public Class<?> getValueClass() {
        return entityClass;
    }
    
    /**
     * Устанавливает класс сущности, используется редактором {@link EntityRefEditor}.
     * @param entityClass Класс сущности для поиска допустимых значений.
     */
    private void setEntityClass(Class<E> entityClass) {
        this.entityClass  = entityClass;
    }
    
    /**
     * Возвращает класс сущности, используется редактором {@link EntityRefEditor}.
     */
    public final Class<E> getEntityClass() {
        return entityClass;
    }

    @Override
    public E getValue() {
        return entityInstance;
    }
    
    /**
     * Возвращает идентификатор сущности.
     */
    public Integer getId() {
        return getValue().getID();
    }

    @Override
    public void setValue(E value) {
        entityInstance = value;
    }
    
    @Override
    public boolean isEmpty() {
        return entityInstance == null;
    }

    @Override
    public IEditorFactory<? extends IComplexType<E, IRefMask<E>>, E> editorFactory() {
        return (IEditorFactory<EntityRef<E>, E>) EntityRefEditor::new;
    }

    /**
     * Установить маску значения.
     */
    @Override
    public EntityRef<E> setMask(IRefMask<E> mask) {
        this.mask = mask;
        return this;
    }

    /**
     * Возвращает маску значения.
     */
    @Override
    public IRefMask<E> getMask() {
        return mask;
    }

    @Override
    public String toString() {
        return entityInstance != null && entityInstance.getID() != null ? entityInstance.getID().toString() : null;
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
    public static <E extends Entity> EntityRef<E> build(Class<E> entityClass, String entityId) {
        return build(entityClass, entityId == null || entityId.isEmpty() ? null : Integer.valueOf(entityId));
    }
    
    /**
     * Построение ссылки на сущность.
     * @param entityClass Класс сущности.
     * @param entityId Идентификатор сущности.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Entity> EntityRef<E> build(Class<E> entityClass, Integer entityId) {
        if (entityClass != null && entityId != null && CAS.isInstanceExists(entityClass, entityId)) {
            Map<String, String> dbValues = CAS.readClassInstance(entityClass, entityId);
            
            EntityRef<E> ownerRef = null;
            try {
                ownerRef = build((Class<E>) CAS.getOwnerClass(entityClass), dbValues.get("OWN"));
            } catch (Exception e) {
                //
            }
            return (EntityRef<E>) Entity.newInstance(entityClass, ownerRef, dbValues.get("PID")).toRef();
        }
        return null;
    }
}
