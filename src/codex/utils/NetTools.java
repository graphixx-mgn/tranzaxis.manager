package codex.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Класс вспомогательных методов дляя работы с сетью.
 */
public class NetTools {
    
    /**
     * Проверка доступности сетевого порта.
     */
    public static boolean isPortAvailable(String ip, int port, int timeout) {
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Invalid port number");
        }
        if (!ip.matches("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$")) {
            throw new IllegalStateException("Invalid IP address");
        }
        try {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), timeout);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }
    
}
