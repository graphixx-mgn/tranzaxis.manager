package codex.instance;

import codex.log.Logger;
import codex.xml.EchoDocument;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.xmlbeans.XmlException;


class LookServer {
    
    private final static Integer GROUP_PORT = 4445;
    private final static String  GROUP_ADDR = "230.0.0.0";
    
    private final Map<NetworkInterface, InetAddress> ifaceAddrs = new LinkedHashMap<>();
    private final List<Instance>  instances = new LinkedList<>();
    private final InetAddress     mcastGroup; 
    private final MulticastSocket mcastSenderSocket;
    private final MulticastSocket mcastReceiverSocket;
    private final ServerSocket    serverSocket;
    private final int             rpcPort;


    LookServer(int rpcPort) throws IOException {
        this.rpcPort    = rpcPort;
        this.mcastGroup = InetAddress.getByName(GROUP_ADDR);
        
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
        
        mcastReceiverSocket = new MulticastSocket(GROUP_PORT);
        mcastReceiverSocket.setReuseAddress(true);
        for (InetAddress address : ifaceAddrs.values()) {
            mcastReceiverSocket.setInterface(address);
            mcastReceiverSocket.joinGroup(mcastGroup);
        }
        
        mcastSenderSocket = new MulticastSocket();
        mcastSenderSocket.setReuseAddress(true);
        
        serverSocket = new ServerSocket(0);
    }
    
    final void start() {
        int length = ifaceAddrs.keySet().stream().mapToInt((iface) -> {
            return iface.getDisplayName().length();
        }).max().getAsInt();
        
        Logger.getLogger().debug(
                "ICS: Found network interfaces:\n{0}",
                ifaceAddrs.keySet().stream().map((iface) -> {
                    return String.format("* [%"+length+"s] : %s", iface.getDisplayName(), ifaceAddrs.get(iface).getHostAddress());
                }).collect(Collectors.joining("\n"))
        );
        
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
    
    private void broadcast(byte[] data) {
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, mcastGroup, GROUP_PORT);
        datagramPacket.getAddress().getHostName();
        try {
            for (InetAddress address : ifaceAddrs.values()) {
                Logger.getLogger().debug("ICS: Send multicast packet to interface: {0}", address.getHostAddress());
                mcastSenderSocket.setInterface(address);
                mcastSenderSocket.send(datagramPacket);
            };
        } catch (IOException e) {
            Logger.getLogger().warn("ICS: Send request packet error", e);
        }
    }
    
//    private void response(byte[] data, InetAddress address, int port) {
//        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
//        try (MulticastSocket datagramSocket = new MulticastSocket()) {
//            datagramSocket.send(datagramPacket);
//        } catch (IOException e) {
//            Logger.getLogger().warn("ICS: Send request packet error", e);
//        }
//    }
    
    byte[] prepareEcho() {
        EchoDocument echoRequest = EchoDocument.Factory.newInstance();
        echoRequest.addNewEcho();
        try {
            echoRequest.getEcho().setHost(InetAddress.getLocalHost().getCanonicalHostName());
        } catch (IOException e) {}
        echoRequest.getEcho().setUser(InstanceCommunicationService.getUserName());
        echoRequest.getEcho().setRpcPort(rpcPort);
        echoRequest.getEcho().setKcaPort(serverSocket.getLocalPort());
        return echoRequest.toString().getBytes();
    }
    
    private boolean connect(Instance instance, boolean forward) {
        synchronized (instances) {
            if (instances.stream().filter((registered) -> {
                    return registered.host.equals(instance.host) && 
                           registered.user.equals(instance.user);
            }).findFirst().orElse(null) == null) {
                linkInstance(instance);
                Logger.getLogger().debug("ICS: Link remote instance {0}", instance);
            } else {
                return false;
            }
        }
        new Thread(() -> {
            try (Socket socket = new Socket() {{
                connect(new InetSocketAddress(instance.address, instance.kcaPort), 1000);
            }}) {
                Logger.getLogger().debug(
                        "ICS: {2} connection established: {0}:{1}", 
                        instance.address, 
                        String.valueOf(instance.kcaPort),
                        forward ? "Forward" : "Backward"
                );
                socket.setKeepAlive(true);
                
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                Logger.getLogger().debug("ICS: Send direct packet to instance: {0}", instance);
                out.println(new String(prepareEcho()));
                out.flush();
                
                socket.getInputStream().read();
            } catch (IOException e) {
                synchronized (instances) {
                    if (instances.contains(instance)) {
                        unlinkInstance(instance);
                        Logger.getLogger().debug("ICS: Unlink remote instance {0}", instance);
                    }
                }
            }
        }).start();
        return true;
    }
    
    protected void linkInstance(Instance instance) {
        instances.add(instance);
    }
    
    protected void unlinkInstance(Instance instance) {
        instances.remove(instance);
    }
    
    class AcceptHandler extends Thread {
        
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
                            Logger.getLogger().debug("ICS: Received direct packet:\nFrom: {0} ({1})\nData: {2}", 
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
                            if (!connect(remoteInstance, false)) {
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
                    boolean ownPacket = ifaceAddrs.values().stream().anyMatch((localAddress) -> {
                        return packet.getSocketAddress().equals(new InetSocketAddress(localAddress, mcastSenderSocket.getLocalPort()));
                    });
                    
                    if (!ownPacket) {
                        EchoDocument echoRqDoc = EchoDocument.Factory.parse(
                            new ByteArrayInputStream(packet.getData(), 0, packet.getLength())
                        );
                        Logger.getLogger().debug("ICS: Received multicast packet:\nFrom: {0} ({1})\nData: {2}", 
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
                        if (!connect(remoteInstance, true)) {
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