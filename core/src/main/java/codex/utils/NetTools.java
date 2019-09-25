package codex.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Класс вспомогательных методов для работы с сетью.
 */
public class NetTools {
    
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
        try {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeout);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }
    
    public static boolean checkPort(int port) {
        return !(port < 1 || port > 65535);
    }
    
    public static boolean checkAddress(String host) {
        return 
                host.matches("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$") &&
                host.matches("^[^\\s]+$");
    }
    
}
