package codex.explorer;

import codex.model.Entity;
import codex.service.IService;
import java.util.List;

/**
 * Интерфейс сервиса доступа к проводнику.
 */
public interface IExplorerAccessService extends IService {
    
    /**
     * Возвращает корень дерева.
     */
    default Entity getRoot() {
        return null;
    }
    
    /**
     * Возвращает список сущностей с заданным классом.
     * @param entityClass Класс сущности.
     */
    default List<? extends Entity> getEntitiesByClass(Class<? extends Entity> entityClass) {
        return null;
    }
    
    /**
     * Возвращает сущность заданного класса по её ID.
     * @param entityClass Класс сущности.
     * @param ID Идентификатор сущности.
     */
    default Entity getEntity(Class<? extends Entity> entityClass, Integer ID) {
        return null;
    }
    
    @Override
    default String getTitle() {
        return "Explorer Access Service";
    }
    
}
