package codex.service;

import codex.log.LoggingSource;
import java.util.ResourceBundle;

/**
 * Настраиваемый сервис. Реализация метода startService создает настроечную
 * сущность и регистрирует в каталоге настроек {@link ServiceRegistry}.
 * @param <T> 
 */
@LoggingSource
@IService.Definition
public abstract class AbstractService<T extends Service> implements IService {

    private T settings;
    private volatile boolean serviceStarted = false;
    
    @Override
    public void startService() {
        synchronized (this) {
            if (!isStarted()) {
                serviceStarted = true;
                getSettings();
                ServiceRegistry.getInstance().getCatalog().insert(settings);
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
    @SuppressWarnings("unchecked")
    public final T getSettings() {
        synchronized (this) {
            if (settings == null) {
                Class<? extends Service> configClass = Service.getServiceConfigClass(
                        Service.class,
                        AbstractService.class,
                        getClass()
                );
                settings = (T) Service.newInstance(configClass, null, getTitle());
                settings.setService(this);
                try {
                    settings.model.commit(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return settings;
        }
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
