package codex.service;

import java.lang.annotation.*;

/**
 * Интерфейс сервиса.
 */
public interface IService {

    /**
     * Возвращает имя сервиса.
     */
    default String getTitle() {
        return "Local service instance ["+getClass().getCanonicalName()+"]";
    }
    
    /**
     * Запуск сервиса - регистрация в модуле управления сервисами.
     */
    default void startService() {}
    
    /**
     * Возвращает признак регистрации сервиса в модуле управления сервисами.
     */
    default boolean isStarted() {
        return true;
    }

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Definition {
        boolean optional() default false;
    }
}