package type;

import codex.type.Iconified;
import codex.utils.ImageUtils;

import javax.swing.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NetInterface implements Iconified {

    private final static ImageIcon ICON_LOCAL   = ImageUtils.getByPath("/images/iface_local.png");
    private final static ImageIcon ICON_GLOBAL  = ImageUtils.getByPath("/images/iface_global.png");
    private final static ImageIcon ICON_VIRTUAL = ImageUtils.getByPath("/images/iface_virtual.png");

    public static List<NetInterface> getInterfaces() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(iface -> {
                        try {
                            return iface.isUp();
                        } catch (SocketException e) {
                            return false;
                        }
                    }).map(NetInterface::new)
                    .collect(Collectors.toList());
        } catch (SocketException e) {
            return Collections.emptyList();
        }
    }

    private NetworkInterface iface;
    private NetInterface (NetworkInterface iface) {
        this.iface = iface;
    }

    public InetAddress getAddress() {
        return Collections.list(iface.getInetAddresses()).stream()
                .filter(inetAddress -> inetAddress instanceof Inet4Address)
                .findFirst().orElse(null);
    }

    @Override
    public ImageIcon getIcon() {
        try {
            return iface.isLoopback() ? ICON_LOCAL : iface.isVirtual() ? ICON_VIRTUAL : ICON_GLOBAL;
        } catch (SocketException e) {
            e.printStackTrace();
            return ICON_GLOBAL;
        }
    }

    @Override
    public String toString() {
        return getAddress().getHostAddress().replaceAll("127.0.0.1", "localhost");
    }
}
