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
    public static boolean isPortAvailable(String host, int port, int timeout) {
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Invalid port number");
        }
        if (!host.matches("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$")) {
            if (!host.matches("^[^\\s]+$")) {
                throw new IllegalStateException("Invalid host address: "+host);
            }
        }
        try {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeout);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }
    
}
