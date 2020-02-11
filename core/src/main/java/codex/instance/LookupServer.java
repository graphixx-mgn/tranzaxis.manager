package codex.instance;

import codex.context.IContext;
import codex.log.Level;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.xml.EchoDocument;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
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

    // Контексты
    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "ICS.Net", name = "Network server events", icon = "/images/network.png", parent = InstanceCommunicationService.class)
    private static class NetContext implements IContext {}

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
    }
    
    /**
     * Удаляет инстанцию из списка подключенных.
     */
    protected void unlinkInstance(Instance instance) {
        instances.remove(instance);
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
                Logger.getContextLogger(NetContext.class).debug("Send multicast request packet to interface: {0}", address.getHostAddress());
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
        } catch (IOException e) {
            //
        }
        echoRequest.getEcho().setUser(getUserName());
        echoRequest.getEcho().setRpcPort(rpcPort);
        echoRequest.getEcho().setKcaPort(serverSocket.getLocalPort());
        return echoRequest.toString().getBytes();
    }

    private static String getUserName() {
        return System.getenv().get("USERNAME");
    }
    
    /**
     * Проверка существования инстанции в реестре, установление соединения и если 
     * метод вызван обработчиком multicast пакетов - отправка ответа непосредственно
     * отправителю.
     * @param instance Инстанция - содержит необходиные данные для подключения.
     * @param forward "Прямое" соединение, требуется отправка ответа.
     */
    private boolean connect(Instance instance, boolean forward) {
        synchronized (instances) {
            if (instances.stream().filter((registered) -> registered.equals(instance)).findFirst().orElse(null) == null) {
                linkInstance(instance);
            } else {
                return false;
            }
        }
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(instance.address, instance.kcaPort), 1000);
                Logger.getContextLogger(NetContext.class).debug(
                        "{0} connection established: {1}:{2}",
                        forward ? "Forward" : "Backward",
                        instance.address,
                        String.valueOf(instance.kcaPort)
                );
                socket.setKeepAlive(true);
                
                if (forward) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream());
                    Logger.getContextLogger(NetContext.class).debug("Send response packet to instance: {0}", instance);
                    out.println(new String(prepareEcho()));
                    out.flush();
                }
                socket.getInputStream().read();
            } catch (IOException e) {
                // Do nothing. Unlink instance in finalization
            } finally {
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
                            Logger.getContextLogger(NetContext.class).debug(
                                    "Received response packet from {0}\n({1})\nData: {2}",
                                    echoRqDoc.getEcho().getHost(),
                                    clientSocket.getRemoteSocketAddress(),
                                    input
                            );
                            Instance remoteInstance = new Instance(
                                    ((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress(),
                                    echoRqDoc.getEcho().getHost(),
                                    echoRqDoc.getEcho().getUser(),
                                    echoRqDoc.getEcho().getRpcPort(), 
                                    echoRqDoc.getEcho().getKcaPort()
                            );
                            connect(remoteInstance, false);
                        } catch (XmlException e) {
                            //
                        }
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                // Do nothing. Close connection in finalization
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    //
                }
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
                    boolean ownPacket = InstanceCommunicationService.IFACE_ADDRS.values().stream()
                            .anyMatch((localAddress) ->
                                    packet.getSocketAddress().equals(new InetSocketAddress(localAddress, mcastSenderSocket.getLocalPort()))
                            );
                    if (!ownPacket) {
                        EchoDocument echoRqDoc = EchoDocument.Factory.parse(
                            new ByteArrayInputStream(packet.getData(), 0, packet.getLength())
                        );
                        Logger.getContextLogger(NetContext.class).debug(
                                "Received request packet from {0}\n({1})\nData: {2}",
                                echoRqDoc.getEcho().getHost(),
                                packet.getSocketAddress(),
                                new String(packet.getData(), 0, packet.getLength())
                        );
                        Instance remoteInstance = new Instance(
                                packet.getAddress(),
                                echoRqDoc.getEcho().getHost(),
                                echoRqDoc.getEcho().getUser(),
                                echoRqDoc.getEcho().getRpcPort(), 
                                echoRqDoc.getEcho().getKcaPort()
                        );
                        connect(remoteInstance, true);
                    }
                } catch (XmlException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    
}