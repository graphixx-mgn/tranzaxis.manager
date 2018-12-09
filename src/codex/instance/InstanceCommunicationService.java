package codex.instance;

import codex.log.Logger;
import codex.service.AbstractService;
import codex.service.CommonServiceOptions;
import codex.service.IRemoteService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;


public final class InstanceCommunicationService extends AbstractService<CommonServiceOptions> implements IInstanceCommunicationService {
    
    public  static ServerSocket RMI_SOCKET;
    private static Registry     RMI_REGISTRY;
    
    private final  List<IInstanceListener> listeners = new LinkedList<>();
    
    static {
        try {
            RMI_SOCKET   = new ServerSocket(0);
            RMI_REGISTRY = LocateRegistry.createRegistry(0, 
                    (String host, int port) -> {
                        throw new UnsupportedOperationException("Not supported yet.");
                    },
                    (port) -> {
                        return RMI_SOCKET;
                    }
            );
        } catch (IOException e) {
            Logger.getLogger().error(e.getMessage());
        }
    }

    private final LookServer lookServer;
    
    public InstanceCommunicationService() throws IOException {
        lookServer = new LookServer(RMI_SOCKET.getLocalPort()) {
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
    }

    @Override
    public boolean isStoppable() {
        return false;
    }

    @Override
    public void startService() {
        super.startService();
        try {
            Logger.getLogger().debug("ICS: Started registry\nHost: {0}\nPort: {1}",
                    InetAddress.getLocalHost().getHostName(), 
                    String.valueOf(RMI_SOCKET.getLocalPort())
            );
        } catch (IOException e) {}
        ServiceLoader<IRemoteService> services = ServiceLoader.load(IRemoteService.class);
        services.forEach(service -> {
            try {
                Logger.getLogger().debug("ICS: register remote service: ''{0}''", service.getTitle());
                RMI_REGISTRY.bind(service.getClass().getCanonicalName(), service);
            } catch (RemoteException e) {
                Logger.getLogger().debug("ICS: remote service ''{0}'' registration error", e);
            } catch (AlreadyBoundException e) {}
        });
        lookServer.start();
    }
    
    public void addInstanceListener(IInstanceListener listener) {
        listeners.add(listener);
    }
    
    public void removeInstanceListener(IInstanceListener listener) {
        listeners.remove(listener);
    }
    
    @Override
    public IRemoteService getService(Class clazz) throws NotBoundException {
        return getService(clazz.getCanonicalName());
    }
    
    @Override
    public IRemoteService getService(String className) throws NotBoundException {
        try {
            return (IRemoteService) RMI_REGISTRY.lookup(className);
        } catch (RemoteException e) {
            throw new IllegalStateException();
        }
    }
    
}