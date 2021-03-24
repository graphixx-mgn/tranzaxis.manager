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
                ServiceRegistry.getInstance().getCatalog().attach(settings);
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
            }
            return settings;
        }
    }
    
}
