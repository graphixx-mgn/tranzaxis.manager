package manager.upgrade.stream;

import java.io.IOException;
import java.rmi.RemoteException;

public interface Readable extends Closeable {

    byte[] read(int count) throws IOException, RemoteException;
    
    public int available() throws IOException, RemoteException;
    
}
