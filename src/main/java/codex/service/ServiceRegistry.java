package codex.service;

import codex.log.Logger;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
    }
    
    private Constructor<MethodHandles.Lookup> lookup;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<Class<? extends IService>, IService> registry = new ConcurrentHashMap<>();
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
    }

    private Class<? extends IService> getServiceInterface(Class<? extends IService> serviceClass) {
        return serviceClass.isInterface() ?
               serviceClass :
               Arrays.stream(serviceClass.getInterfaces())
                    .filter(IService.class::isAssignableFrom)
                    .map(aClass -> (Class<? extends IService>) aClass.asSubclass(IService.class))
                    .findFirst()
                    .get();
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
        Class<? extends IService> serviceInterface = getServiceInterface(service.getClass());
        if (!registry.containsKey(serviceInterface)) {
            registry.put(serviceInterface, createServiceProxy(serviceInterface, service));
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

//    public boolean isServiceRegistered(Class<? extends IService> serviceClass) {
//        return registry.containsKey(serviceClass);
//    }

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
        Class<? extends IService> srvIface = getServiceInterface(serviceInterface);

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
        Stream<LocalServiceOptions> stream =
                serviceCatalog == null ? Stream.empty() :
                        serviceCatalog.childrenList().stream()
                                .map((node) -> (LocalServiceOptions) node);
        Optional<LocalServiceOptions> serviceControl = stream
                .filter((control) -> serviceInterface.isAssignableFrom(control.getService().getClass()))
                .findFirst();

        return !serviceControl.isPresent() || serviceControl.get().isStarted();
    }

    @SuppressWarnings("unchecked")
    private <T extends IService> T createServicePreloader(Class<T> serviceInterface) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class[]{ serviceInterface },
                (Object proxy, Method method, Object[] arguments) -> {
                    if (isEnabled(serviceInterface)) {
                        if (registry.containsKey(serviceInterface)) {
                            IService service = registry.get(serviceInterface);
                            return method.invoke(service, arguments);
                        }
                        if (!method.getName().equals("getTitle")) {
                            T preloader = (T) proxy;
                            Logger.getLogger().warn(
                                    "Called not registered service ''{0}''\nService call: {1}({2})",
                                    preloader.getTitle(),
                                    method.getName(),
                                    Arrays.stream(method.getParameterTypes())
                                            .map((param) -> "<".concat(param.getSimpleName()).concat(">"))
                                            .collect(Collectors.joining(", "))
                            );
                        }
                    }
                    return lookup.newInstance(serviceInterface,
                            MethodHandles.Lookup.PRIVATE
                    )
                        .unreflectSpecial(method, method.getDeclaringClass())
                        .bindTo(proxy)
                        .invokeWithArguments(arguments);
                }
        );
    }

    private IService createServiceProxy(Class<? extends IService> serviceInterface, IService service) {
        return (IService) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class[]{ serviceInterface },
                (Object proxy, Method method, Object[] arguments) -> method.invoke(service, arguments)
        );
    }

    @FunctionalInterface
    public interface IRegistryListener {
        void serviceRegistered(IService service);
    }
    
}
