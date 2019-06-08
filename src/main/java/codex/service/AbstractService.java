package codex.service;

import codex.model.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Настраиваемый сервис. Реализация метода startService создает настроечную
 * сущность и регистрирует в каталоге настроек {@link ServiceRegistry}.
 * @param <T> 
 */
public abstract class AbstractService<T extends LocalServiceOptions> implements IService {
    
    private final static Map<Class, Boolean> STARTED_SERVICES = new HashMap<>();
    private T serviceConfig;
    
    /**
     * Возвращает признак возможности остановить сервис.
     */
    public boolean isStoppable() {
        return true;
    }
    
    @Override
    public void startService() {
        synchronized (this) {
            if (!Boolean.TRUE.equals(STARTED_SERVICES.get(getClass()))) {
                STARTED_SERVICES.put(getClass(), Boolean.TRUE);
                LocalServiceOptions control = getConfig();
                control.setService(this);
                ServiceRegistry.getInstance().getCatalog().insert(control);
            }
        }
    }
    
    @Override
    public boolean isStarted() {
        return Boolean.TRUE.equals(STARTED_SERVICES.get(getClass()));
    };
    
    /**
     * Получение сущности с настройками сервиса.
     */
    public final T getConfig() {
        if (serviceConfig == null) {
            serviceConfig = Entity.newInstance(ServiceCatalog.getServiceConfigClass(
                    LocalServiceOptions.class,
                    AbstractService.class,
                    getClass()
            ), null, getTitle());
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
