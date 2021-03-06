package codex.service;

import codex.log.Logger;
import codex.utils.Runtime;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Реестр сервисов приложения. 
 */
public final class ServiceRegistry {
    
    private static final ServiceRegistry INSTANCE = new ServiceRegistry();
    public  static ServiceRegistry getInstance() {
        return INSTANCE;
    }
    static {
        Logger.getLogger().debug("Service Registry: load local services...");
        ServiceLoader<IService> services = ServiceLoader.load(IService.class);
        Iterator<IService> iterator = services.iterator();
        while (iterator.hasNext()) {
            try {
                INSTANCE.registerService(iterator.next(), false);
            } catch (ServiceConfigurationError e) {
                Logger.getLogger().warn("Service Registry: unable to initialize service", e.getCause());
            }
        }
        INSTANCE.registry.values().forEach((service) -> {
            if (!service.isStarted()) {
                service.startService();
                Logger.getLogger().debug("Service Registry: start service: ''{0}''", service.getTitle());
            }
        });
        Runtime.systemInfo();
    }

    private final Map<Class<? extends IService>, IService> registry = new ConcurrentHashMap<>();
    private final Map<Class<? extends IService>, List<IRegistryListener>> listeners = new ConcurrentHashMap<>();
    
    private ServiceCatalog serviceCatalog;
    
    private ServiceRegistry() {
        Logger.getLogger().debug("Initialize Service Registry");
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
    private void registerService(IService service, boolean startImmediately) {
        Class<? extends IService> serviceInterface = IService.getServiceInterface(service.getClass());
        if (!registry.containsKey(serviceInterface)) {
            registry.put(serviceInterface, service);
            Logger.getLogger().debug("Service Registry: register service ''{0}''", service.getTitle());
            if (startImmediately) {
                service.startService();
            }
            new LinkedHashMap<>(listeners).forEach((serviceClass, listenerList) -> {
                if (serviceClass.isAssignableFrom(service.getClass())) {
                    listenerList.forEach((listener) -> listener.serviceRegistered(service));
                }
            });
        }
    }

    public boolean isServiceRegistered(Class<? extends IService> serviceClass) {
        return registry.containsKey(IService.getServiceInterface(serviceClass));
    }

    /**
     * Поиск сервиса в реестре. Если сервис не был ранее зарегистрирован, возвращаеется
     * объект заглушка, руализующий методы по-умолчанию и выдается ппредупреждающее
     * сообщение в трассу о поппытке запроса к несуществующему сервису.
     */
    @SuppressWarnings("unchecked")
    public <T extends IService> T lookupService(Class<T> serviceInterface) {
        if (!serviceInterface.isInterface()) {
            throw new IllegalStateException(serviceInterface+" is not an interface");
        }
        if (registry.containsKey(serviceInterface) && isEnabled(serviceInterface)) {
            T service = (T) registry.get(serviceInterface);
            if (!service.isStarted()) {
                service.startService();
            }
            return service;
        } else {
            return createServicePreloader(serviceInterface);
        }
    }
    
    public final void addRegistryListener(IRegistryListener listener) {
        addRegistryListener(IService.class, listener);
    }
    
    public final void addRegistryListener(Class<? extends IService> serviceInterface, IRegistryListener listener) {
        Class<? extends IService> srvIface = IService.getServiceInterface(serviceInterface);

        if (!listeners.containsKey(srvIface)) {
            listeners.put(srvIface, new LinkedList<>());
        }
        listeners.get(srvIface).add(listener);
        registry.values().forEach((service) -> {
            if (srvIface.isAssignableFrom(service.getClass())) {
                listener.serviceRegistered(service);
            }
        });
    }

    /**
     * Возвращает каталог настроек сервисов {@link AbstractService}.
     */
    final ServiceCatalog getCatalog() {
        if (serviceCatalog == null) {
            serviceCatalog = new ServiceCatalog();
        }
        return serviceCatalog;
    }

    /**
     * Возвращает признак включен ли сервис в настройках.
     * @param serviceInterface Класс интерфейса сервиса.
     */
    private boolean isEnabled(Class<? extends IService> serviceInterface) {
        Stream<Service> stream = serviceCatalog == null ?
                Stream.empty() :
                serviceCatalog.childrenList().stream().map((node) -> (Service) node);
        Optional<Service> serviceControl = stream
                .filter((control) -> serviceInterface.isAssignableFrom(control.getService().getClass()))
                .findFirst();
        return !serviceControl.isPresent() || serviceControl.get().isEnabled();
    }

    //@SuppressWarnings("unchecked")
    private <T extends IService> T createServicePreloader(Class<T> serviceInterface) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (registry.containsKey(serviceInterface)) {
                IService service = registry.get(serviceInterface);
                return method.invoke(service, args);
            } else {
                Exception exception = new Exception();
                Logger.getLogger().warn(
                        "Called not registered service ''{0}''\nMethod: {1}({2})\nLocation: {3}",
                        serviceInterface.getTypeName(),
                        method.getName(),
                        Arrays.stream(method.getParameterTypes())
                                .map((param) -> "<".concat(param.getSimpleName()).concat(">"))
                                .collect(Collectors.joining(", ")),
                        exception.getStackTrace()[2]
                );
                return null;
            }
        };
        Object proxy = Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] {serviceInterface},
                handler
        );
        return serviceInterface.cast(proxy);
    }

    @FunctionalInterface
    public interface IRegistryListener {
        void serviceRegistered(IService service);
    }
    
}
