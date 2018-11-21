package codex.service;

import codex.log.Logger;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Реестр сервисов приложения. 
 */
public final class ServiceRegistry {
    
    private final static ServiceRegistry INSTANCE = new ServiceRegistry();
    
    private final Map<Class, IService> registry = new HashMap<>();
    private final Map<Class, IService> stubs = new HashMap<>();
    private Constructor<MethodHandles.Lookup> lookup;
    
    private ServiceCatalog serviceCatalog;
    
    private ServiceRegistry() {
        try {
            Logger.getLogger().debug("Initialize Service Registry");
            lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Integer.TYPE);
            if (!lookup.isAccessible()) {
                lookup.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            Logger.getLogger().error("Unable to start Service Registry", e);
        }
    };
    
    /**
     * Возвращает singletone - экземпляр реестра.
     */
    public static ServiceRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Возвращает каталог настроек сервисов {@link AbstractService}.
     */
    public final ServiceCatalog getCatalog() {
        if (serviceCatalog == null) {
            serviceCatalog = new ServiceCatalog();
        }
        return serviceCatalog;
    }
    
    /**
     * Регистрация реализации сервиса в реестре. Каждый сервис мжет быть только 
     * в одном экземпляре. Запуск сервиса будет отложен до первого обращения.
     * @param service Регистрируемый сервис.
     */
    public void registerService(IService service) {
        registerService(service, true);
    }
    
    /**
     * Регистрация реализации сервиса в реестре. Каждый сервис мжет быть только 
     * в одном экземпляре.
     * @param service Регистрируемый сервис.
     * @param startImmediately Запустить сервис после регистрации.
     */
    public void registerService(IService service, boolean startImmediately) {
        registry.put(service.getClass(), service);
        Logger.getLogger().debug("Service Registry: register service ''{0}''", service.getTitle());
        if (startImmediately) {
            registry.get(service.getClass()).startService();
        }
    }
    
    /**
     * Поиск сервиса в реестре. Если сервис не был ранее зарегистрирован, возвращаеется
     * объект заглушка, руализующий методы по-умолчанию и выдается ппредупреждающее
     * сообщение в трассу о поппытке запроса к несуществующему сервису.
     */
    public IService lookupService(Class serviceClass) {
        if (registry.containsKey(serviceClass) && !registry.get(serviceClass).isStarted()) {
            registry.get(serviceClass).startService();
        }
        if (registry.containsKey(serviceClass) && isEnabled(serviceClass)) {
            return registry.get(serviceClass);
        } else {
            Class serviceInterface = serviceClass.getInterfaces()[0];
            try {
                //https://stackoverflow.com/questions/37812393/how-to-explicitly-invoke-default-method-from-a-dynamic-proxy
                if (!stubs.containsKey(serviceInterface)) {
                    stubs.put(serviceInterface, 
                        (IService) Proxy.newProxyInstance(serviceInterface.getClassLoader(),
                            new Class[]{serviceInterface}, 
                            (Object proxy, Method method, Object[] arguments) -> {
                                boolean isEnabled = isEnabled(serviceClass);
                                if (registry.containsKey(serviceClass) && isEnabled) {
                                    for (Method classMethod : registry.get(serviceClass).getClass().getMethods()) {
                                        if (classMethod.getName().equals(method.getName())) {
                                            try {
                                                return classMethod.invoke(registry.get(serviceClass), arguments);
                                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                if (!method.getName().equals("getTitle") && isEnabled) {
                                    Logger.getLogger().warn(
                                            "Called not registered service ''{0}'' ", 
                                            stubs.get(serviceInterface).getTitle()
                                    );
                                }
                                return lookup.newInstance(serviceInterface,
                                        MethodHandles.Lookup.PRIVATE
                                )
                                    .unreflectSpecial(method, method.getDeclaringClass())
                                    .bindTo(proxy)
                                    .invokeWithArguments(arguments);
                            }
                        )
                    );
                }
                return stubs.get(serviceInterface);
            } catch (IllegalArgumentException | SecurityException e1) {
                Logger.getLogger().error(MessageFormat.format(
                        "Service ''{0}'' invocation error", 
                        stubs.get(serviceInterface).getTitle()
                ), e1);
                return null;
            }
        }
    }
    
    /**
     * Возвращает признак включен ли сервис в настройках.
     * @param serviceClass Класс сервиса. 
     */
    private boolean isEnabled(Class serviceClass) {
        Stream<CommonServiceOptions> stream = 
                serviceCatalog == null ? Stream.empty() : 
                serviceCatalog.childrenList().stream().map((node) -> {
                    return (CommonServiceOptions) node;
                });
        Optional<CommonServiceOptions> serviceConrtrol = 
                stream.filter((control) -> {
                    return control.getService().getClass().equals(serviceClass);
                }).findFirst();
        
        return !serviceConrtrol.isPresent() || serviceConrtrol.get().isStarted();
    }
    
}
