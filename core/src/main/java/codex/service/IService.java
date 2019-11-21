package codex.service;

import java.lang.annotation.*;
import java.util.Arrays;

/**
 * Интерфейс сервиса.
 */
public interface IService {

    static Class<? extends IService> getServiceInterface(Class<? extends IService> serviceClass) {
        return serviceClass.isInterface() ?
                serviceClass :
                Arrays.stream(serviceClass.getInterfaces())
                        .filter(IService.class::isAssignableFrom)
                        .map(aClass -> (Class<? extends IService>) aClass.asSubclass(IService.class))
                        .findFirst()
                        .get();
    }

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