package codex.service;

import codex.log.LoggingSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ResourceBundle;

/**
 * Настраиваемый сервис. Реализация метода startService создает настроечную
 * сущность и регистрирует в каталоге настроек {@link ServiceRegistry}.
 * @param <T> 
 */
@LoggingSource
@IService.Definition
public abstract class AbstractService<T extends LocalServiceOptions> implements IService {

    private T serviceConfig;
    private boolean serviceStarted = false;
    
    @Override
    public void startService() {
        synchronized (this) {
            if (!serviceStarted) {
                serviceStarted = true;
                ServiceRegistry.getInstance().getCatalog().insert(getConfig());
            }
        }
    }
    
    @Override
    public boolean isStarted() {
        return serviceStarted;
    }
    
    /**
     * Получение сущности с настройками сервиса.
     */
    public final T getConfig() {
        if (serviceConfig == null) {
            Class<? extends LocalServiceOptions> configClass = ServiceCatalog.getServiceConfigClass(
                    LocalServiceOptions.class,
                    AbstractService.class,
                    getClass()
            );
            try {
                Constructor<? extends LocalServiceOptions> ctor = configClass.getConstructor(getClass());
                serviceConfig = (T) ctor.newInstance(this);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return serviceConfig;
    }
    
    /**
     * Получение настроек сервиса из META-INF/options/{Имя класса}.
     * @param key Значение ключа, по которому выбирается строка-значение.
     */
    public final String getOption(String key) {
        if (ClassLoader.getSystemClassLoader().getResource("META-INF/options/"+getClass().getSimpleName()+".properties") != null) {
            return ResourceBundle.getBundle("META-INF/options/"+getClass().getSimpleName()).getString(key);
        } else {
            return null;
        }
    }
    
}
