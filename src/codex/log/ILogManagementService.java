package codex.log;

import codex.service.IService;

/**
 * Интерфейс сервиса управления модулем логирования.
 */
public interface ILogManagementService extends IService {
    
    /**
     * Установить новые значения отображения событий.
     */
    default void setLevel(Level level) {};
    
    @Override
    default String getTitle() {
        return "Logger Management Service";
    };
    
}
