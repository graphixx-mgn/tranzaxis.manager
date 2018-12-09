package codex.instance;

import codex.service.IRemoteService;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;


public final class Instance implements IInstanceCommunicationService {
    
    final String       host;
    final String       user;
    final InetAddress  address;
    final int rpcPort, kcaPort;
    final Registry     registry;
    
    Instance(InetAddress address, String host, String user, int rpcPort, int kcaPort) throws RemoteException {
        this.host     = host;
        this.user     = user;
        this.address  = address;
        this.rpcPort  = rpcPort;
        this.kcaPort  = kcaPort;
        this.registry = LocateRegistry.getRegistry(address.getHostName(), rpcPort);
    }
    
    @Override
    public String toString() {
        return MessageFormat.format("[host={0}, user={1}, addr={2}]", host, user, address);
    }
    
    public Map<String, IRemoteService> getServices() throws RemoteException {
        Map<String, IRemoteService> registered = new HashMap<>();
        for (String className : registry.list()) {
            try {
                registered.put(className, (IRemoteService) registry.lookup(className));
            } catch (NotBoundException e) {}
        }
        return registered;
    }
    
    @Override
    public IRemoteService getService(Class clazz) throws RemoteException, NotBoundException {
        return getService(clazz.getCanonicalName());
    }
    
    @Override
    public IRemoteService getService(String className) throws RemoteException, NotBoundException {
        return (IRemoteService) registry.lookup(className);
    }
    
}
