package codex.instance;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MultihomeRMIClientSocketFactory implements RMIClientSocketFactory, Serializable {
    
    private static final long serialVersionUID = 7033753601964541325L;

    private final String[] hosts;

    public MultihomeRMIClientSocketFactory(final String[] hosts) {
        this.hosts = hosts;
    }

    @Override
    public Socket createSocket(String hostString, final int port) throws IOException {
        if (hosts.length < 2) {
            return this.factory().createSocket(hostString, port);
        }
        final List<IOException> exceptions = new ArrayList<>();
        final Selector selector = Selector.open();
        for (final String host : hosts) {
            final SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().setSoTimeout(1000);
            channel.register(selector, SelectionKey.OP_CONNECT);
            final SocketAddress addr = new InetSocketAddress(host, port);
            channel.connect(addr);
        }

        SocketChannel connectedChannel;
        connect: 
        while (true) {
            if (selector.keys().isEmpty()) {
                throw new IOException("Connection failed for " + hostString + ": " + exceptions);
            }
            selector.select(1000);
            final Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                throw new IOException("Selection keys unexpectedly empty for " + hostString + "[exceptions: " + exceptions + "]");
            }
            for (final SelectionKey key : keys) {
                final SocketChannel channel = (SocketChannel) key.channel();
                key.cancel();
                try {
                    channel.configureBlocking(true);
                    channel.finishConnect();
                    connectedChannel = channel;
                    break connect;
                } catch (final IOException e) {
                    exceptions.add(e);
                }
            }
        }

        for (final SelectionKey key : selector.keys()) {
            final Channel channel = key.channel();
            if (channel != connectedChannel) {
                channel.close();
            }
        }

        final Socket socket = connectedChannel.socket();
        try {
            if (RMISocketFactory.getSocketFactory() == null) {
                return socket;
            }
            String host = socket.getInetAddress().getHostAddress();
            socket.close();
            return factory().createSocket(host, port);
        } finally {
            selector.close();
        }
    }

    @Override
    public boolean equals(final Object x) {
        if (x.getClass() != this.getClass()) {
            return false;
        }
        return true;
    }

    private RMIClientSocketFactory factory() {
        final RMIClientSocketFactory f = RMISocketFactory.getSocketFactory();
        if (f != null) {
            return f;
        }
        return RMISocketFactory.getDefaultSocketFactory();
    }

    @Override
    public int hashCode() {
        return this.getClass().hashCode();
    }
}