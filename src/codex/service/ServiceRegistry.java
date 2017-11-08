package codex.service;

import codex.config.IConfigStoreService;
import codex.log.Logger;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Реестр сервисов приложения. 
 */
public final class ServiceRegistry {
    
    private final static ServiceRegistry INSTANCE = new ServiceRegistry();
    
    private Registry registry = null;
    private final Map<Class, IService> stubs = new HashMap<>();
    private Constructor<MethodHandles.Lookup> lookup;
    
    private ServiceRegistry() {
        try {
            Logger.getLogger().debug("Initialize Service Registry");
            registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
            lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Integer.TYPE);
            if (!lookup.isAccessible()) {
                lookup.setAccessible(true);
            }
        } catch (RemoteException | NoSuchMethodException e) {
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
     * Регистрация реализации сервиса в реестре. Каждый сервис мжет быть только 
     * в одном экземпляре.
     */
    public void registerService(IService service) {
        if (registry != null) {
            try {
                Remote serviceInstance = UnicastRemoteObject.exportObject(service, 0);
                registry.bind(service.getClass().getCanonicalName(), serviceInstance);
                Logger.getLogger().debug("Service Registry: register service ''{0}''", service.getClass().getCanonicalName());
            } catch (RemoteException e) {
                Logger.getLogger().error(MessageFormat.format("Unable to bind service ''{0}''", service.getClass().getCanonicalName()), e);
            } catch (AlreadyBoundException e) {}
        }
    }
    
    /**
     * Поиск сервиса в реестре. Если сервис не был ранее зарегистрирован, возвращаеется
     * объект заглушка, руализующий методы по-умолчанию и выдается ппредупреждающее
     * сообщение в трассу о поппытке запроса к несуществующему сервису.
     */
    public IService lookupService(Class serviceClass) {
        try {
            return (IService) registry.lookup(serviceClass.getCanonicalName());
        } catch (RemoteException | NotBoundException e) {
            Class prototype = serviceClass.getInterfaces()[0];
            try {
                //https://stackoverflow.com/questions/37812393/how-to-explicitly-invoke-default-method-from-a-dynamic-proxy
                if (!stubs.containsKey(serviceClass)) {
                    stubs.put(
                        serviceClass, 
                        (IService) Proxy.newProxyInstance(
                            IConfigStoreService.class.getClassLoader(),
                            new Class[]{IConfigStoreService.class}, 
                            (Object proxy, Method method, Object[] arguments) -> {
                                Logger.getLogger().warn("Called not registered service ''{0}'' ", serviceClass.getCanonicalName());
                                return lookup.newInstance(
                                        IConfigStoreService.class,
                                        MethodHandles.Lookup.PRIVATE
                                )
                                    .unreflectSpecial(method, method.getDeclaringClass())
                                    .bindTo(proxy)
                                    .invokeWithArguments(arguments);
                            }
                        )
                    );
                }
                return stubs.get(serviceClass);
            } catch (IllegalArgumentException | SecurityException e1) {
                Logger.getLogger().error(
                        MessageFormat.format("Service ''{0}'' invocation error", serviceClass.getCanonicalName()),
                        e1
                );
                return null;
            }
        }
    }
    
}
