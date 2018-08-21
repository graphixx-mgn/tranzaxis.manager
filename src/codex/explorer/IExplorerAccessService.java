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
     */
    default List<Entity> getEntitiesByClass(Class entityClass) {
        return null;
    }
    
    /**
     * Возвращает сущность заданного класса по её ID.
     */
    default Entity getEntity(Class entityClass, Integer ID) {
        return null;
    }
    
    @Override
    default String getTitle() {
        return "Explorer Access Service";
    }
    
}
