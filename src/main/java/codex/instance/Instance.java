package codex.instance;

import codex.service.IRemoteService;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Класс-контейнер для хранения информации об удаленной инстанции и 
 * обращения к её сервисам.
 */
public final class Instance implements IInstanceCommunicationService {
    
    final String       host;
    final String       user;
    final InetAddress  address;
    final int rpcPort, kcaPort;
    final Registry     registry;
    
    /**
     * Конструктор инстанции.
     * @param address Сетевой адрес инстанции.
     * @param host Имя хоста инстанции.
     * @param user Имя пользователя операционной системы, запустивший инстанцию.
     * @param rpcPort Номер порта реестра сетевых сервисов (RMI).
     * @param kcaPort НОмер порта для установки постоянного соединения. 
     * Используется для отслеживания разрыва соединения.
     */
    Instance(InetAddress address, String host, String user, int rpcPort, int kcaPort) throws RemoteException {
        this.host     = host;
        this.user     = user;
        this.address  = address;
        this.rpcPort  = rpcPort;
        this.kcaPort  = kcaPort;
        this.registry = LocateRegistry.getRegistry(address.getHostAddress(), rpcPort);
    }
    
    @Override
    public String toString() {
        return MessageFormat.format("[host={0}, user={1}, addr={2}]", host, user, address);
    }
    
    /**
     * Возвращает адрес сокета реестра сетевых сервисов.
     */
    public final InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress(address, rpcPort);
    }
    
    /**
     * Возвращает карту имен и ссылок сетевых сервисов.
     */
    @Override
    public Map<String, IRemoteService> getServices() throws RemoteException {
        Map<String, IRemoteService> registered = new HashMap<>();
        for (String className : registry.list()) {
            try {
                registered.put(className, (IRemoteService) registry.lookup(className));
            } catch (NotBoundException e) {
                //
            }
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

    @Override
    public boolean equals(Object obj) {
        Instance instance = (Instance) obj;
        return host.equals(instance.host) && 
               user.equals(instance.user) &&
               address.equals(instance.address);
    }

    public String getUser() {
        return user;
    }
}
