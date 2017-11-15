package codex.service;

import codex.log.Logger;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Реестр сервисов приложения. 
 */
public final class ServiceRegistry {
    
    private final static ServiceRegistry INSTANCE = new ServiceRegistry();
    
    private final Map<Class, IService> registry = new HashMap<>();
    private final Map<Class, IService> stubs = new HashMap<>();
    private Constructor<MethodHandles.Lookup> lookup;
    
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
     * Регистрация реализации сервиса в реестре. Каждый сервис мжет быть только 
     * в одном экземпляре.
     */
    public void registerService(IService service) {
        registry.put(service.getClass(), service);
        Logger.getLogger().debug("Service Registry: register service ''{0}''", service.getTitle());
    }
    
    /**
     * Поиск сервиса в реестре. Если сервис не был ранее зарегистрирован, возвращаеется
     * объект заглушка, руализующий методы по-умолчанию и выдается ппредупреждающее
     * сообщение в трассу о поппытке запроса к несуществующему сервису.
     */
    public IService lookupService(Class serviceClass) {
        if (registry.containsKey(serviceClass)) {
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
                                if (!method.getName().equals("getTitle")) {
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
    
}
