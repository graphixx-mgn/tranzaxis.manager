package manager.commands.build;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


public class BuildingNotifier extends UnicastRemoteObject implements IBuildingNotifier {
    
    private final Map<UUID, IBuildListener> listeners = new LinkedHashMap<>();
    
    public BuildingNotifier() throws RemoteException {}

    @Override
    public void setProgress(String uid, int percent) {
        listeners.get(UUID.fromString(uid)).setProgress(percent);
    }

    @Override
    public void setStatus(String uid, String text) {
        listeners.get(UUID.fromString(uid)).setStatus(text);
    }

    @Override
    public void failed(String uid, Throwable ex) {
        listeners.get(UUID.fromString(uid)).failed(ex);
    }
    
    @Override
    public void finished(String uid) throws RemoteException {
        listeners.get(UUID.fromString(uid)).finished();
    }
    
    public void addListener(UUID uuid, IBuildListener listener) {
        listeners.put(uuid, listener);
    }
    
    public void removeListener(UUID uuid) {
        listeners.remove(uuid);
    }

    @Override
    public void checkPaused(String uid) throws RemoteException {
        listeners.get(UUID.fromString(uid)).checkPaused();
    }
 
}
