package codex.instance;

import codex.context.IContext;
import codex.service.*;
import codex.log.Logger;
import net.jcip.annotations.ThreadSafe;
import org.atteo.classindex.ClassIndex;
import java.io.IOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис взаимодействия инстанций.
 */
@ThreadSafe
@IContext.Definition(id = "ICS", name = "Instance Dispatcher", icon = "/images/localhost.png")
public final class InstanceCommunicationService extends AbstractService<CommunicationServiceOptions> implements IInstanceDispatcher, IContext {
    
    /**
     * Карта доступных сетевых интерфейсов и их IP адресов.
     */
    public static Map<NetworkInterface, InetAddress> IFACE_ADDRS = Collections.unmodifiableMap(loadInterfaces());
    
    private final ServerSocket rmiSocket = new ServerSocket(0);
    private final Registry     rmiRegistry = LocateRegistry.createRegistry(0, null, (port) -> rmiSocket);
    private final LookupServer lookupServer = new LookupServer(rmiSocket.getLocalPort()) {
        @Override
        protected void linkInstance(Instance instance) {
            super.linkInstance(instance);
            Logger.getLogger().debug("Link remote instance {0}", instance);
            synchronized (listeners) {
                listeners.forEach((listener) -> listener.instanceLinked(instance));
            }
        }
        @Override
        protected void unlinkInstance(Instance instance) {
            super.unlinkInstance(instance);
            Logger.getLogger().debug("Unlink remote instance {0}", instance);
            synchronized (listeners) {
                listeners.forEach((listener) -> listener.instanceUnlinked(instance));
            }
        }
    };
    
    private final List<IInstanceListener> listeners = new LinkedList<>();
    
    public InstanceCommunicationService() throws IOException {}

    @Override
    public void startService() {
        super.startService();
        int length = IFACE_ADDRS.keySet().stream()
                .mapToInt((iface) -> iface.getDisplayName().length())
                .max().getAsInt();
        
        Logger.getLogger().debug("Started RMI service registry on port: {0}",
                String.valueOf(rmiSocket.getLocalPort())
        );
        Logger.getLogger().debug(
                "Bind RMI registry and lookup server to network interfaces:\n{0}",
                IFACE_ADDRS.keySet().stream().map((iface) -> {
                    return String.format("* [%-"+length+"s] : %s", iface.getDisplayName(), InstanceCommunicationService.IFACE_ADDRS.get(iface).getHostAddress());
                }).collect(Collectors.joining("\n"))
        );
        ClassIndex.getSubclasses(IRemoteService.class).forEach(this::registerRemoteService);
        lookupServer.start();
    }

    @Override
    public void registerRemoteService(Class<? extends IRemoteService> remoteServiceClass) {
        try {
            IRemoteService service = remoteServiceClass.getConstructor().newInstance();
            rmiRegistry.rebind(service.getClass().getCanonicalName(), service);
            getSettings().attach(((AbstractRemoteService) service).getConfiguration());
            Logger.getLogger().debug("Registered remote service: ''{0}''", service.getTitle());
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause() instanceof ServiceNotLoadedException) {
                ServiceNotLoadedException serviceException = (ServiceNotLoadedException) e.getCause();
                Logger.getLogger().log(serviceException.getLevel(), serviceException.getMessage());
            } else {
                Logger.getLogger().warn("Unable to register remote service", e);
            }
        }
    }

    @Override
    public void addInstanceListener(IInstanceListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
        getInstances().forEach(listener::instanceLinked);
    }

    @Override
    public void removeInstanceListener(IInstanceListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public Map<String, IRemoteService> getServices() throws RemoteException {
        Map<String, IRemoteService> services = new LinkedHashMap<>();
        for (String className : rmiRegistry.list()) {
            try {
                services.put(className, getService(className));
            } catch (NotBoundException ignore) {}
        }
        return services;
    }

    @Override
    public IRemoteService getService(Class clazz) throws NotBoundException {
        return getService(clazz.getCanonicalName());
    }
    
    @Override
    public IRemoteService getService(String className) throws NotBoundException {
        try {
            return (IRemoteService) rmiRegistry.lookup(className);
        } catch (RemoteException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public List<Instance> getInstances() {
        return lookupServer.getInstances();
    }
    
    private static Map<NetworkInterface, InetAddress> loadInterfaces() {
        Map<NetworkInterface, InetAddress> ifaceAddrs = new LinkedHashMap<>();
        try {
            final Enumeration<NetworkInterface> netifs = NetworkInterface.getNetworkInterfaces();
            while (netifs.hasMoreElements()) {
                NetworkInterface iface = netifs.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> inAddrs = iface.getInetAddresses();
                while (inAddrs.hasMoreElements()) {
                    InetAddress inAddr = inAddrs.nextElement();
                    if (inAddr instanceof Inet4Address) {
                        ifaceAddrs.put(iface, inAddr);
                    }
                }
            }
        } catch (SocketException e) {
            //
        }
        return ifaceAddrs;
    }
    
}