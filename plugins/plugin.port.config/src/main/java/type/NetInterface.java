package type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.stream.Collectors;

public class NetInterface implements Iconified {

    private final static ImageIcon ICON_LOCAL   = ImageUtils.getByPath("/images/iface_local.png");
    private final static ImageIcon ICON_GLOBAL  = ImageUtils.getByPath("/images/iface_global.png");
    private final static ImageIcon ICON_VIRTUAL = ImageUtils.getByPath("/images/iface_virtual.png");

    public static List<NetInterface> getInterfaces() {
        Map<NetworkInterface, InetAddress> ifaceAddrs = new LinkedHashMap<>();
        try {
            final Enumeration<NetworkInterface> netifs = NetworkInterface.getNetworkInterfaces();
            while (netifs.hasMoreElements()) {
                NetworkInterface iface = netifs.nextElement();
                if (!iface.isUp()) continue;

                Enumeration<InetAddress> inAddrs = iface.getInetAddresses();
                while (inAddrs.hasMoreElements()) {
                    InetAddress inAddr = inAddrs.nextElement();
                    if (inAddr instanceof Inet4Address) {
                        ifaceAddrs.put(iface, inAddr);
                    }
                }
            }
        } catch (SocketException e) {
            //
        }
        return ifaceAddrs.entrySet().stream()
                .map(entry -> new NetInterface(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private final NetworkInterface iface;
    private final InetAddress address;

    private NetInterface (NetworkInterface iface, InetAddress address) {
        this.iface   = iface;
        this.address = address;
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
        return address.getHostAddress().replaceAll("127.0.0.1", "localhost");
    }
}
