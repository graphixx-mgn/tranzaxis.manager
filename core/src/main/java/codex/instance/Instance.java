package codex.instance;

import codex.service.IRemoteService;
import codex.service.ServiceRegistry;
import net.jcip.annotations.ThreadSafe;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Класс-контейнер для хранения информации об удаленной инстанции и 
 * обращения к её сервисам.
 */
@ThreadSafe
public final class Instance implements IInstanceCommunicationService {
    
    final String       host;
    final String       user;
    final InetAddress  address;
    final int rpcPort, kcaPort;

    public static Instance getRemoteInstance() {
        try {
            String clientIP = RemoteServer.getClientHost();
            return ServiceRegistry.getInstance().lookupService(IInstanceDispatcher.class).getInstances().stream()
                    .filter(instance -> instance.getRemoteAddress().getAddress().getHostAddress().equals(clientIP))
                    .findFirst().orElse(null);
        } catch (ServerNotActiveException e) {
            //
        }
        return null;
    }
    
    /**
     * Конструктор инстанции.
     * @param address Сетевой адрес инстанции.
     * @param host Имя хоста инстанции.
     * @param user Имя пользователя операционной системы, запустивший инстанцию.
     * @param rpcPort Номер порта реестра сетевых сервисов (RMI).
     * @param kcaPort НОмер порта для установки постоянного соединения. 
     * Используется для отслеживания разрыва соединения.
     */
    Instance(InetAddress address, String host, String user, int rpcPort, int kcaPort) {
        this.host     = host;
        this.user     = user;
        this.address  = address;
        this.rpcPort  = rpcPort;
        this.kcaPort  = kcaPort;
    }
    
    @Override
    public String toString() {
        return MessageFormat.format(
                "[host={0}, user={1}, addr={2}]",
                host, user, address.getHostAddress()
        );
    }

    /**
     * Возвращает имя пользователя удаленной инстанции.
     */
    public String getUser() {
        return user;
    }

    /**
     * Возвращает адрес сокета реестра сетевых сервисов.
     */
    public final InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress(address, rpcPort);
    }

    private Registry getRegistry() throws RemoteException {
        return LocateRegistry.getRegistry(address.getHostAddress(), rpcPort, (host, port) -> {
            Socket s = new Socket(host, port);
            s.setSoTimeout(500);
            return s;
        });
    }

    /**
     * Возвращает карту имен и ссылок сетевых сервисов.
     */
    @Override
    public Map<String, IRemoteService> getServices() throws RemoteException {
        final Map<String, IRemoteService> registered = new HashMap<>();
        final Registry registry = getRegistry();
        for (String className : registry.list()) {
            try {
                Class.forName(className);
                registered.put(className, (IRemoteService) registry.lookup(className));
            } catch (NotBoundException | ClassNotFoundException ignore) {}
        }
        return registered;
    }
    
    @Override
    public IRemoteService getService(Class clazz) throws RemoteException, NotBoundException {
        return getService(clazz.getCanonicalName());
    }
    
    @Override
    public IRemoteService getService(String className) throws RemoteException, NotBoundException {
        return (IRemoteService) getRegistry().lookup(className);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        return host.equals(instance.host) &&
                user.equals(instance.user) &&
                address.equals(instance.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, user, address);
    }
}
