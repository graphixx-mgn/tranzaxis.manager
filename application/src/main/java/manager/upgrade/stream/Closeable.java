package manager.upgrade.stream;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Closeable extends Remote {
    
    public void close() throws IOException, RemoteException;
    
}
