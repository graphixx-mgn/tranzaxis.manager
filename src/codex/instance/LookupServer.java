package codex.instance;

import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.xml.EchoDocument;
import com.sun.javafx.PlatformUtil;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import org.apache.xmlbeans.XmlException;


/**
 * Сервер поиска и подключения инстанций. Реализует сетевой обмен пакета между инстанциями
 * установку постоянных соединений и отслеживание обрывов.
 */
class LookupServer {
    
    private final static Integer GROUP_PORT = 4445;
    private final static String  GROUP_ADDR = "230.0.0.0";
    
    private final List<Instance>  instances = new LinkedList<>();
    private final InetAddress     mcastGroup; 
    private final MulticastSocket mcastSenderSocket;
    private final MulticastSocket mcastReceiverSocket;
    private final ServerSocket    serverSocket;
    private final int             rpcPort;
    
    private InstanceCommunicationService ICS;

    /**
     * Конструктор сервера.
     * @param rpcPort Номер порта локально реестра сетевых сервисов. Передатся
     * в сетевых пакетах.
     */
    LookupServer(int rpcPort) throws IOException {
        this.rpcPort    = rpcPort;
        this.mcastGroup = InetAddress.getByName(GROUP_ADDR);

        mcastReceiverSocket = new MulticastSocket(GROUP_PORT);
        mcastReceiverSocket.setReuseAddress(true);
        for (InetAddress address : InstanceCommunicationService.IFACE_ADDRS.values()) {
            mcastReceiverSocket.setInterface(address);
            mcastReceiverSocket.joinGroup(mcastGroup);
        }
        
        mcastSenderSocket = new MulticastSocket();
        mcastSenderSocket.setReuseAddress(true);
        
        serverSocket = new ServerSocket(0);
    }
    
    /**
     * Запуск сервера.
     */
    final void start() {
        ICS = (InstanceCommunicationService) ServiceRegistry.getInstance().lookupService(InstanceCommunicationService.class);
        new Thread(new RequestHandler(mcastReceiverSocket)).start();
        new Thread(() -> {
            try {
                while (true) {
                    new AcceptHandler(serverSocket.accept()).start();
                }
            } catch (IOException e) {
                Logger.getLogger().warn("Lookup: Unknown server socket error", e);
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    Logger.getLogger().warn("Lookup: close server socket error", e);
                }
            }
        }).start();
    }
    
    /**
     * Возвращает список подключенных инстанций.
     */
    List<Instance> getInstances() {
        return new LinkedList<>(instances);
    }
    
    /**
     * Добавляет инстанцию в список подключенных.
     */
    protected void linkInstance(Instance instance) {
        instances.add(instance);
        if (ICS.getConfig().isShowNetOps()) {
            Logger.getLogger().debug("ICS: Link remote instance {0}", instance);
        }
    }
    
    /**
     * Удаляет инстанцию из списка подключенных.
     */
    protected void unlinkInstance(Instance instance) {
        instances.remove(instance);
        if (ICS.getConfig().isShowNetOps()) {
            Logger.getLogger().debug("ICS: Unlink remote instance {0}", instance);
        }
    }
    
    /**
     * Посылка multicast-пакета для поиска запущенных инстанций.
     * @param data Байт-массив пакета.
     */
    private void broadcast(byte[] data) {
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, mcastGroup, GROUP_PORT);
        datagramPacket.getAddress().getHostName();
        try {
            for (InetAddress address : InstanceCommunicationService.IFACE_ADDRS.values()) {
                if (ICS.getConfig().isShowNetOps()) {
                    Logger.getLogger().debug("ICS: Send multicast request packet to interface: {0}", address.getHostAddress());
                }
                mcastSenderSocket.setInterface(address);
                mcastSenderSocket.send(datagramPacket);
            }
        } catch (IOException e) {
            Logger.getLogger().warn("ICS: Send request packet error", e);
        }
    }
    
    /**
     * Подготовка байт-массива эхо-пакета.
     * Пакет представляет собой XML-документ вида:
     * <pre>{@code
     * <Echo host="..." user="..." rpcPort="..." kcaPort="..."/>
     * где
     * * host - Имя локального хоста 
     * * user - Имя локального пользователя
     * * rpcPort - Номер порта реестра сетевых сервисов (RMI).
     * * kcaPort - Номер порта для установки постоянного соединения. 
     * }</pre>
     */
    private byte[] prepareEcho() {
        EchoDocument echoRequest = EchoDocument.Factory.newInstance();
        echoRequest.addNewEcho();
        try {
            echoRequest.getEcho().setHost(InetAddress.getLocalHost().getCanonicalHostName());
        } catch (IOException e) {}
        echoRequest.getEcho().setUser(getUserName());
        echoRequest.getEcho().setRpcPort(rpcPort);
        echoRequest.getEcho().setKcaPort(serverSocket.getLocalPort());
        return echoRequest.toString().getBytes();
    }

    private static String getUserName() {
        try {
            String className  = null;
            String methodName = "getUsername";
            if (PlatformUtil.isWindows()) {
                className = "com.sun.security.auth.module.NTSystem";
                methodName = "getName";
            } else if (PlatformUtil.isUnix()) {
                className = "com.sun.security.auth.module.UnixSystem";
            }

            if (className != null) {
                Class<?> c = Class.forName(className);
                Method method = c.getDeclaredMethod( methodName );
                Object o = c.newInstance();
                return (String) method.invoke(o);
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
    
    /**
     * Проверка существования инстанции в реестре, установление соединения и если 
     * метод вызван обработчиком multicast пакетов - отправка ответа непосредственно
     * отправителю.
     * @param instance Инстанция - содержит необходиные данные для подключения.
     * @param forward "Прямое" соединение, требуется отправка ответа.
     * @return 
     */
    private boolean connect(Instance instance, boolean forward) {
        synchronized (instances) {
            if (instances.stream().filter((registered) -> {
                    return registered.host.equals(instance.host) && 
                           registered.user.equals(instance.user);
            }).findFirst().orElse(null) == null) {
                linkInstance(instance);
            } else {
                return false;
            }
        }
        new Thread(() -> {
            try (Socket socket = new Socket() {{
                connect(new InetSocketAddress(instance.address, instance.kcaPort), 1000);
            }}) {
                if (ICS.getConfig().isShowNetOps()) {
                    Logger.getLogger().debug(
                            "ICS: {2} connection established: {0}:{1}", 
                            instance.address, 
                            String.valueOf(instance.kcaPort),
                            forward ? "Forward" : "Backward"
                    );
                }
                socket.setKeepAlive(true);
                
                if (forward) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream());
                    if (ICS.getConfig().isShowNetOps()) {
                        Logger.getLogger().debug("ICS: Send response packet to instance: {0}", instance);
                    }
                    out.println(new String(prepareEcho()));
                    out.flush();
                }
                socket.getInputStream().read();
            } catch (IOException e) {
                synchronized (instances) {
                    if (instances.contains(instance)) {
                        unlinkInstance(instance);
                    }
                }
            }
        }).start();
        return true;
    }
    
    /**
     * Обработчик ответов.
     */
    private class AcceptHandler extends Thread {
        
        private final Socket clientSocket;
        private AcceptHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String input = bufferedReader.readLine();
                    if (input != null && !input.isEmpty()) {
                        try {
                            EchoDocument echoRqDoc = EchoDocument.Factory.parse(input);
                            if (ICS.getConfig().isShowNetOps()) {
                                Logger.getLogger().debug("ICS: Received response packet:\nFrom: {0} ({1})\nData: {2}", 
                                        echoRqDoc.getEcho().getHost(),
                                        clientSocket.getRemoteSocketAddress(),
                                        input
                                );
                            }
                            Instance remoteInstance = new Instance(
                                    ((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress(),
                                    echoRqDoc.getEcho().getHost(),
                                    echoRqDoc.getEcho().getUser(),
                                    echoRqDoc.getEcho().getRpcPort(), 
                                    echoRqDoc.getEcho().getKcaPort()
                            );
                            if (!connect(remoteInstance, false) && ICS.getConfig().isShowNetOps()) {
                                Logger.getLogger().debug("ICS: Skip duplicate linkage of instance {0}", remoteInstance);
                            }
                        } catch (XmlException e) {}
                    }
                }
            } catch (IOException e) {
                try {
                    clientSocket.close();
                } catch (IOException e1) {}
            }
        }
    
    }
    
    /**
     * Обработчик multicast-запросов.
     */
    private class RequestHandler implements Runnable {
        
        private final MulticastSocket socket;
        
        RequestHandler(MulticastSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            byte[] buf = new byte[256];
            broadcast(prepareEcho());
            
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    Logger.getLogger().warn("ICS: Socket error", e);
                    continue;
                }
                try {
                    boolean ownPacket = InstanceCommunicationService.IFACE_ADDRS.values().stream().anyMatch((localAddress) -> {
                        return packet.getSocketAddress().equals(new InetSocketAddress(localAddress, mcastSenderSocket.getLocalPort()));
                    });
                    
                    if (!ownPacket) {
                        EchoDocument echoRqDoc = EchoDocument.Factory.parse(
                            new ByteArrayInputStream(packet.getData(), 0, packet.getLength())
                        );
                        if (ICS.getConfig().isShowNetOps()) {
                            Logger.getLogger().debug("ICS: Received request packet:\nFrom: {0} ({1})\nData: {2}", 
                                    echoRqDoc.getEcho().getHost(),
                                    packet.getSocketAddress(),
                                    new String(packet.getData(), 0, packet.getLength())
                            );
                        }
                        Instance remoteInstance = new Instance(
                                packet.getAddress(),
                                echoRqDoc.getEcho().getHost(),
                                echoRqDoc.getEcho().getUser(),
                                echoRqDoc.getEcho().getRpcPort(), 
                                echoRqDoc.getEcho().getKcaPort()
                        );
                        if (!connect(remoteInstance, true) && ICS.getConfig().isShowNetOps()) {
                            Logger.getLogger().debug("ICS: Skip duplicate linkage of instance {0}", remoteInstance);
                        }
                    }
                } catch (XmlException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    
}