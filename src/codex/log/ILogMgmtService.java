package codex.log;

import codex.service.IService;
import java.util.Map;

/**
 * Интерфейс сервиса управления модулем логирования.
 */
public interface ILogMgmtService extends IService {
    
    /**
     * Установить новые значения отображения событий.
     */
    default void changeLevels(Map<Level, Boolean> levels) {};
    
}
