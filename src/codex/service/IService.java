package codex.service;

/**
 * Интерфейс сервиса.
 */
public interface IService {

    /**
     * Возвращает имя сервиса.
     */
    default String getTitle() {
        return "IService interface instance ["+getClass().getCanonicalName()+"]";
    };
    
    /**
     * Запуск сервиса - регистрация в модуле управления сервисами.
     */
    default void startService() {};
    
    /**
     * Возвращает признак регистрации сервиса в модуле управления сервисами.
     */
    default boolean isStarted() {
        return true;
    };
    
}