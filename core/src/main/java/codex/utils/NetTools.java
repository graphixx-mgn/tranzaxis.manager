package codex.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Класс вспомогательных методов для работы с сетью.
 */
public class NetTools {

    private final static Map<String, Object> syncMap = new HashMap<>();
    
    /**
     * Проверка доступности сетевого порта.
     * @param host Символьное имя хоста или IP-адрес.
     * @param port Номер порта.
     * @param timeout Таймаут подключения в миллисекундах.
     */
    public static boolean isPortAvailable(String host, int port, int timeout) throws IllegalStateException {
        if (!checkPort(port)) {
            throw new IllegalStateException("Invalid port number");
        }
        if (!checkAddress(host)) {
            throw new IllegalStateException("Invalid host address: "+host);
        }

        Object syncVal;
        String syncKey = host.concat(":").concat(String.valueOf(port));
        synchronized (syncMap) {
            syncVal = syncMap.computeIfAbsent(syncKey, s -> new Object());
        }
        synchronized (syncVal) {
            SocketAddress remoteAddr = new InetSocketAddress(host, port);
            try (Socket socket = new Socket()) {
                int attempt = 0;
                while (!socket.isConnected() && attempt < 3) {
                    try {
                        socket.connect(remoteAddr, timeout);
                    } catch (IOException ignore) {}
                    attempt++;
                }
                return socket.isConnected();
            } catch (IOException e) {
                return false;
            } finally {
                syncMap.remove(syncKey);
            }
        }
    }
    
    private static boolean checkPort(int port) {
        return !(port < 1 || port > 65535);
    }
    
    private static boolean checkAddress(String host) {
        return 
                host.matches("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$") &&
                host.matches("^[^\\s]+$");
    }
    
}
