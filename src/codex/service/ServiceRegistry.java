package codex.service;

import codex.log.Logger;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Реестр сервисов приложения. 
 */
public final class ServiceRegistry {
    
    private static final ServiceRegistry INSTANCE = new ServiceRegistry();
    static {
        Logger.getLogger().debug("Service Registry: load local services...");
        ServiceLoader<IService> services = ServiceLoader.load(IService.class);
        services.forEach(service -> {
            INSTANCE.registerService(service, false);
        });
        
        INSTANCE.registry.values().forEach((service) -> {
            if (!service.isStarted()) {
                Logger.getLogger().debug("Service Registry: start service: ''{0}''", service.getTitle());
                service.startService();
            }
        });
    }
    
    private Constructor<MethodHandles.Lookup> lookup;
    
    private final Map<Class<? extends IService>, IService> stubs = new HashMap<>();
    private final Map<Class<? extends IService>, IService> registry = new LinkedHashMap<>();
    private final Map<Class<? extends IService>, List<IRegistryListener>> listeners = new ConcurrentHashMap<>();
    
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
        
        new LinkedHashMap<>(listeners).forEach((serviceClass, listenerList) -> {
            if (serviceClass.isAssignableFrom(service.getClass())) {
                listenerList.forEach((listener) -> {
                    listener.serviceRegistered(service);
                });
            }
        });
    }
    
    public boolean isServiceRegistered(Class<? extends IService> serviceClass) {
        return registry.containsKey(serviceClass);
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
                                            "Called not registered service ''{0}''\nService call: {1}({2})", 
                                            stubs.get(serviceInterface).getTitle(),
                                            method.getName(),
                                            Arrays.asList(method.getParameterTypes()).stream().map((param) -> {
                                                return "<".concat(param.getSimpleName()).concat(">");
                                            }).collect(Collectors.joining(", "))
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
    
    public final void addRegistryListener(IRegistryListener listener) {
        addRegistryListener(IService.class, listener);
    }
    
    public final void addRegistryListener(Class<? extends IService> serviceClass, IRegistryListener listener) {
        if (!listeners.containsKey(serviceClass)) {
            listeners.put(serviceClass, new LinkedList<>());
        }
        listeners.get(serviceClass).add(listener);
        
        registry.values().forEach((service) -> {
            if (serviceClass.isAssignableFrom(service.getClass())) {
                listener.serviceRegistered(service);
            }
        });
    }
    
    @FunctionalInterface
    public interface IRegistryListener {
        
        void serviceRegistered(IService service);
        
    }
    
}
