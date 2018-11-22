package codex.log;

import codex.service.IService;
import java.util.Map;

/**
 * Интерфейс сервиса управления модулем логирования.
 */
public interface ILogManagementService extends IService {
    
    /**
     * Установить новые значения отображения событий.
     */
    default void changeLevels(Map<Level, Boolean> levels) {};
    
    @Override
    default String getTitle() {
        return "Logger Management Service";
    };
    
}
