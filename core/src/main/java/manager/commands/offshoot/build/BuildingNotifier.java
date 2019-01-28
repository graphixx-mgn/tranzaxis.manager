package manager.commands.offshoot.build;

import org.radixware.kernel.common.check.RadixProblem;
import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


public class BuildingNotifier extends UnicastRemoteObject implements IBuildingNotifier {
    
    private final Map<UUID, IBuildListener> listeners = new LinkedHashMap<>();
    
    public BuildingNotifier() throws RemoteException {}

    void addListener(UUID uuid, IBuildListener listener) {
        synchronized (listeners) {
            listeners.put(uuid, listener);
        }
    }

    void removeListener(UUID uuid) {
        synchronized (listeners) {
            listeners.remove(uuid);
        }
    }

    @Override
    public void error(UUID uuid, Throwable ex) throws RemoteException {
        synchronized (listeners) {
            if (listeners.containsKey(uuid)) {
                new LinkedHashMap<>(listeners).get(uuid).error(ex);
            }
        }
    }

    @Override
    public void event(UUID uuid, RadixProblem.ESeverity severity, String defId, String name, ImageIcon icon, String message) throws RemoteException {
        synchronized (listeners) {
            if (listeners.containsKey(uuid)) {
                new LinkedHashMap<>(listeners).get(uuid).event(severity, defId, name, icon, message);
            }
        }
    }

    @Override
    public void progress(UUID uuid, int percent) throws RemoteException {
        synchronized (listeners) {
            if (listeners.containsKey(uuid)) {
                new LinkedHashMap<>(listeners).get(uuid).progress(percent);
            }
        }
    }

    @Override
    public void description(UUID uuid, String text) throws RemoteException {
        synchronized (listeners) {
            if (listeners.containsKey(uuid)) {
                new LinkedHashMap<>(listeners).get(uuid).description(text);
            }
        }
    }

    @Override
    public void isPaused(UUID uuid) throws RemoteException {
        synchronized (listeners) {
            if (listeners.containsKey(uuid)) {
                new LinkedHashMap<>(listeners).get(uuid).isPaused();
            }
        }
    }
}
