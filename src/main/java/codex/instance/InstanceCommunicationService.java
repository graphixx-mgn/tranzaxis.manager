package codex.instance;

import codex.service.*;
import codex.log.Logger;
import java.io.IOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис взаимодействия инстанций.
 */
public final class InstanceCommunicationService extends AbstractService<CommunicationServiceOptions> implements IInstanceCommunicationService {
    
    /**
     * Карта доступных сетевых интерфейсов и их IP адресов.
     */
    public static Map<NetworkInterface, InetAddress> IFACE_ADDRS = loadInterfaces();
    
    private final ServerSocket rmiSocket = new ServerSocket(0);
    private final Registry     rmiRegistry = LocateRegistry.createRegistry(0, null, (port) -> rmiSocket);
    private final LookupServer lookupServer = new LookupServer(rmiSocket.getLocalPort()) {
        @Override
        protected void linkInstance(Instance instance) {
            super.linkInstance(instance);
            new LinkedList<>(listeners).forEach((listener) -> {
                listener.instanceLinked(instance);
            });
        }
        @Override
        protected void unlinkInstance(Instance instance) {
            super.unlinkInstance(instance);
            new LinkedList<>(listeners).forEach((listener) -> {
                listener.instanceUnlinked(instance);
            });
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
        
        Logger.getLogger().debug("ICS: Started RMI service registry on port: {0}",
                String.valueOf(rmiSocket.getLocalPort())
        );
        Logger.getLogger().debug(
                "ICS: Bind RMI registry and lookup server to network interfaces:\n{0}",
                IFACE_ADDRS.keySet().stream().map((iface) -> {
                    return String.format("* [%-"+length+"s] : %s", iface.getDisplayName(), InstanceCommunicationService.IFACE_ADDRS.get(iface).getHostAddress());
                }).collect(Collectors.joining("\n"))
        );
        lookupServer.start();
        
        ServiceLoader<IRemoteService> services = ServiceLoader.load(IRemoteService.class);
        Iterator<IRemoteService> iterator = services.iterator();
        while (iterator.hasNext()) {
            try {
                IRemoteService service = iterator.next();
                Logger.getLogger().debug("ICS: register remote service: ''{0}''", service.getTitle());
                rmiRegistry.rebind(service.getClass().getCanonicalName(), service);
                getConfig().insert(((AbstractRemoteService) service).getConfiguration());

            } catch (RemoteException e) {
                // Do nothing
            } catch (ServiceConfigurationError e) {
                Throwable cause = e.getCause();
                if (cause instanceof ServiceNotLoadedException) {
                    Logger.getLogger().warn("ICS: {0}", cause.getMessage());
                } else {
                    Logger.getLogger().warn("ICS: remote service ''{0}'' registration error", cause);
                }
            }
        }
    }
    
    /**
     * Добавить слушателя события подключения инстанции.
     */
    public void addInstanceListener(IInstanceListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Удалить слушателя события подключения инстанции.
     */
    public void removeInstanceListener(IInstanceListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Map<String, IRemoteService> getServices() throws RemoteException {
        Map<String, IRemoteService> services = new LinkedHashMap<>();
        for (String className : rmiRegistry.list()) {
            try {
                services.put(className, getService(className));
            } catch (NotBoundException e) {
                //
            }
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

    /**
     * Возвращяет список подключенных инстанций.
     */
    public List<Instance> getInstances() {
        return lookupServer.getInstances();
    }

    public Instance getClientInstance() {
        try {
            String clientIP = RemoteServer.getClientHost();
            return getInstances().stream()
                    .filter(instance -> instance.getRemoteAddress().getAddress().getHostAddress().equals(clientIP))
                    .findFirst().orElse(null);
        } catch (ServerNotActiveException e) {
            //
        }
        return null;
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