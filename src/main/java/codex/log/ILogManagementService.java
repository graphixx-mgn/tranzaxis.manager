package codex.log;

import codex.service.IService;

/**
 * Интерфейс сервиса управления модулем логирования.
 */
public interface ILogManagementService extends IService {
    
    @Override
    default String getTitle() {
        return "Logger Management Service";
    }

    void log(Level level, String message);

    void debug(String message, Object... params);
    void  info(String message, Object... params);
    void  warn(String message, Object... params);
    void  warn(String message, Throwable exception);
    void error(String message, Object... params);
    void error(String message, Throwable exception);
    
}
