package codex.explorer;

import codex.model.Entity;
import codex.service.IService;
import java.util.List;

/**
 * Интерфейс сервиса доступа к проводнику.
 */
public interface IExplorerAccessService extends IService {
    
    /**
     * Возвращает список сущностей с заданным классом.
     */
    default List<Entity> getEntitiesByClass(Class entityClass) {
        return null;
    }
    
    @Override
    default String getTitle() {
        return "Explorer Access Service";
    }
    
}
