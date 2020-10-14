package codex.instance;

import codex.context.IContext;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.xml.EchoDocument;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import net.jcip.annotations.ThreadSafe;
import org.apache.xmlbeans.XmlException;

/**
 * Сервер поиска и подключения инстанций. Реализует сетевой обмен пакета между инстанциями
 * установку постоянных соединений и отслеживание обрывов.
 */
@ThreadSafe
class LookupServer {
    
    private final static Integer GROUP_PORT = 4445;
    private final static String  GROUP_ADDR = "230.0.0.0";

    private final List<Instance> instances  = new LinkedList<>();

    private final Discover discover;

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
        discover = new Discover(rpcPort);
    }
    
    /**
     * Запуск сервера.
     */
    final void start() {
       discover.start();
    }
    
    /**
     * Возвращает список подключенных инстанций.
     */
    List<Instance> getInstances() {
        synchronized (instances) {
            return new LinkedList<>(instances);
        }
    }
    
    /**
     * Добавляет инстанцию в список подключенных.
     */
    protected void linkInstance(Instance instance) {
        synchronized (instances) {
            if (instances.stream().noneMatch(registered -> registered.equals(instance))) {
                instances.add(instance);
            }
        }
    }
    
    /**
     * Удаляет инстанцию из списка подключенных.
     */
    protected void unlinkInstance(Instance instance) {
        synchronized (instances) {
            instances.remove(instance);
        }
    }

    private class Discover extends Thread {

        private final int rpcPort, kcaPort;
        private final InetAddress group = InetAddress.getByName(GROUP_ADDR);
        private final DatagramChannel     udpChannel = DatagramChannel.open(StandardProtocolFamily.INET);
        private final ServerSocketChannel tcpChannel = ServerSocketChannel.open();
        private final Selector            selector   = Selector.open();

        private Discover(int rpcPort) throws IOException {
            setDaemon(true);
            setName("Discover thread");

            udpChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            udpChannel.bind(new InetSocketAddress(GROUP_PORT));
            udpChannel.configureBlocking(false);
            for (NetworkInterface netInterface : InstanceCommunicationService.IFACE_ADDRS.keySet()) {
                udpChannel.setOption(StandardSocketOptions.IP_MULTICAST_IF, netInterface);
                udpChannel.join(group, netInterface);
            }

            tcpChannel.configureBlocking( false );
            tcpChannel.socket().bind(new InetSocketAddress(0));

            this.rpcPort = rpcPort;
            this.kcaPort = tcpChannel.socket().getLocalPort();
        }

        @Override
        public void run() {
            try {
                udpChannel.register(selector, SelectionKey.OP_READ,   new UdpServerHandler());
                tcpChannel.register(selector, SelectionKey.OP_ACCEPT, new TcpServerHandler());
                broadcast(prepareEcho());

                while (true) {
                    try {
                        selector.select();
                        Set<SelectionKey> events = selector.selectedKeys();
                        new HashSet<>(events).forEach(event -> {
                            events.remove(event);
                            if (event.isValid()) {
                                if (event.isAcceptable()) {
                                    ServerSocketHandler socketHandler = (ServerSocketHandler) event.attachment();
                                    socketHandler.accept(event);
                                } else if (event.isConnectable()) {
                                    ClientSocketHandler socketHandler = (ClientSocketHandler) event.attachment();
                                    socketHandler.connect(event);
                                } else if (event.isReadable()) {
                                    SocketHandler socketHandler = (SocketHandler) event.attachment();
                                    socketHandler.read(event);
                                } else if (event.isWritable()) {
                                    ClientSocketHandler socketHandler = (ClientSocketHandler) event.attachment();
                                    socketHandler.write(event);
                                }
                            }
                        });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                Logger.getLogger().warn("Unable to start discover thread", e);
            }
        }

        /**
         * Посылка multicast-пакета для поиска запущенных инстанций.
         * @param data Байт-массив пакета.
         */
        private void broadcast(byte[] data) {
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, group, GROUP_PORT);
            try (MulticastSocket mcastSenderSocket = new MulticastSocket()) {
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
            final EchoDocument echoRequest = EchoDocument.Factory.newInstance();
            final EchoDocument.Echo echo = echoRequest.addNewEcho();
            try {
                echo.setHost(InetAddress.getLocalHost().getCanonicalHostName());
            } catch (IOException e) {
                e.printStackTrace();
            }
            echo.setUser(getUserName());
            echo.setRpcPort(rpcPort);
            echo.setKcaPort(kcaPort);
            return echoRequest.xmlText().getBytes();
        }

        private String getUserName() {
            return System.getenv().get("USERNAME");
        }


        private abstract class SocketHandler {
            abstract void read(SelectionKey event);
        }

        private abstract class ServerSocketHandler extends SocketHandler {
            final EchoDocument parseMessage(byte[] data) {
                try {
                    return EchoDocument.Factory.parse(new ByteArrayInputStream(data));
                } catch (XmlException | IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            abstract void accept(SelectionKey event);
        }

        private abstract class ClientSocketHandler extends SocketHandler {

            private final Instance instance;
            private final boolean  sendEcho;

            ClientSocketHandler(Instance instance, boolean sendEcho) {
                this.instance = instance;
                this.sendEcho = sendEcho;
            }

            abstract void connect(SelectionKey event);
            abstract void write(SelectionKey event);

            protected final Instance getInstance() {
                return instance;
            }

            protected final boolean needEcho() {
                return sendEcho;
            }
        }

        private class UdpServerHandler extends ServerSocketHandler {

            @Override
            void read(SelectionKey event) {
                final ByteBuffer buffer = ByteBuffer.allocate(256);
                buffer.clear();
                DatagramChannel datagramChannel = (DatagramChannel) event.channel();
                try {
                    InetSocketAddress remoteAddress = (InetSocketAddress) datagramChannel.receive(buffer);
                    buffer.flip();
                    processMessage(remoteAddress, Arrays.copyOf(buffer.array(), buffer.remaining()));
                } catch (IOException e) {
                    Logger.getLogger().warn("Unexpected error", e);
                }
            }

            private boolean isRemoteMessage(InetSocketAddress remoteAddress) {
                return InstanceCommunicationService.IFACE_ADDRS.values().stream()
                        .noneMatch(localAddress -> localAddress.getHostAddress().equals(remoteAddress.getHostString()));
            }

            final void processMessage(InetSocketAddress remoteAddress, byte[] data) {
                if (remoteAddress != null && isRemoteMessage(remoteAddress)) {
                    EchoDocument message = parseMessage(data);
                    if (message != null) {
                        Logger.getContextLogger(NetContext.class).debug(
                                "Received echo packet from {0} ({1})\nData: {2}",
                                message.getEcho().getHost(),
                                remoteAddress.getHostString(),
                                new String(data)
                        );
                        try {
                            SocketChannel socketChannel = SocketChannel.open();
                            socketChannel.configureBlocking(false);
                            socketChannel.socket().setSoTimeout(1000);
                            socketChannel.register(
                                    selector,
                                    SelectionKey.OP_CONNECT,
                                    new TcpClientHandler(
                                            new Instance(
                                                    remoteAddress.getAddress(),
                                                    message.getEcho().getHost(),
                                                    message.getEcho().getUser(),
                                                    message.getEcho().getRpcPort(),
                                                    message.getEcho().getKcaPort()
                                            ),
                                            true
                                    )
                            );
                            socketChannel.connect(new InetSocketAddress(
                                    remoteAddress.getAddress(),
                                    message.getEcho().getKcaPort()
                            ));
                        } catch (IOException e) {
                            Logger.getLogger().warn("Unexpected error", e);
                        }
                    }
                }
            }

            @Override
            void accept(SelectionKey event) {
                throw new IllegalStateException();
            }
        }

        private class TcpServerHandler extends ServerSocketHandler {

            @Override
            void read(SelectionKey event) {
                final ByteBuffer buffer = ByteBuffer.allocate(256);
                buffer.clear();
                SocketChannel socketChannel = (SocketChannel) event.channel();
                try {
                    InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                    socketChannel.read(buffer);
                    buffer.flip();
                    processMessage(
                            socketChannel,
                            remoteAddress,
                            Arrays.copyOf(buffer.array(), buffer.remaining())
                    );
                } catch (IOException ignore) {}
            }

            @Override
            void accept(SelectionKey event) {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) event.channel();
                try {
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(
                            selector,
                            SelectionKey.OP_READ,
                            this
                    );
                } catch (IOException e) {
                    Logger.getLogger().warn("Unexpected error", e);
                }
            }

            final synchronized void processMessage(SocketChannel socketChannel, InetSocketAddress remoteAddress, byte[] data) throws ClosedChannelException {
                if (remoteAddress != null) {
                    EchoDocument message = parseMessage(data);
                    if (message != null) {
                        Logger.getContextLogger(NetContext.class).debug(
                                "Received echo packet from {0} ({1})\nData: {2}",
                                message.getEcho().getHost(),
                                remoteAddress.getHostString(),
                                new String(data)
                        );
                        Instance instance = new Instance(
                                remoteAddress.getAddress(),
                                message.getEcho().getHost(),
                                message.getEcho().getUser(),
                                message.getEcho().getRpcPort(),
                                message.getEcho().getKcaPort()
                        );
                        socketChannel.register(
                                selector,
                                SelectionKey.OP_READ,
                                new TcpClientHandler(instance, false)
                        );
                        linkInstance(instance);
                    }
                }
            }
        }

        private class TcpClientHandler extends ClientSocketHandler {

            TcpClientHandler(Instance instance, boolean sendEcho) {
                super(instance, sendEcho);
            }

            @Override
            void read(SelectionKey event) {
                final ByteBuffer buffer = ByteBuffer.allocate(256);
                buffer.clear();
                SocketChannel socketChannel = (SocketChannel) event.channel();
                try {
                    socketChannel.read(buffer);
                } catch (IOException e) {
                    Logger.getContextLogger(NetContext.class).debug(
                            "Connection lost: {0}:{1}",
                            getInstance().address, String.valueOf(getInstance().kcaPort)
                    );
                    unlinkInstance(getInstance());
                    event.cancel();
                }
            }

            @Override
            void connect(SelectionKey event) {
                SocketChannel socketChannel = (SocketChannel) event.channel();
                try {
                    socketChannel.finishConnect();
                    Logger.getContextLogger(NetContext.class).debug(
                            "Connection established: {0}:{1}",
                            getInstance().address, String.valueOf(getInstance().kcaPort)
                    );
                    linkInstance(getInstance());
                    event.interestOps(needEcho() ? SelectionKey.OP_WRITE : SelectionKey.OP_READ);
                } catch (IOException e) {
                    Logger.getContextLogger(NetContext.class).debug(
                            "Unable to establish connection: {0}:{1}",
                            getInstance().address, String.valueOf(getInstance().kcaPort)
                    );
                    event.cancel();
                }
            }

            @Override
            void write(SelectionKey event) {
                try {
                    SocketChannel socketChannel = (SocketChannel) event.channel();
                    Logger.getContextLogger(NetContext.class).debug(
                            "Sent response packet: {0}:{1}",
                            getInstance().address, String.valueOf(getInstance().kcaPort)
                    );
                    socketChannel.write(ByteBuffer.wrap(prepareEcho()));
                } catch (IOException e) {
                    Logger.getLogger().warn("Unexpected error", e);
                } finally {
                    event.interestOps(SelectionKey.OP_READ);
                }
            }
        }
    }
}