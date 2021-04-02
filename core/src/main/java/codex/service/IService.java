package codex.service;

import java.lang.annotation.*;
import java.util.Arrays;
import java.util.ResourceBundle;

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
     * Получение настроек сервиса из META-INF/options/{Имя класса}.
     * @param key Значение ключа, по которому выбирается строка-значение.
     */
    default String getOption(String key) {
        if (ClassLoader.getSystemClassLoader().getResource("META-INF/options/"+getClass().getSimpleName()+".properties") != null) {
            return ResourceBundle.getBundle("META-INF/options/"+getClass().getSimpleName()).getString(key);
        } else {
            return null;
        }
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