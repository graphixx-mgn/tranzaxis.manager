package manager.upgrade.stream;

import codex.instance.InstanceCommunicationService;
import codex.instance.MultihomeRMIClientSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.stream.Collectors;

//https://dzone.com/articles/java-io-streams-and-rmi
public class RemoteInputStreamServer extends UnicastRemoteObject implements Readable {

    private static final byte EMPTY_BUFFER[] = new byte[0];
    
    private final InputStream in;
	
    public RemoteInputStreamServer(InputStream in) throws RemoteException {
        super(0, new MultihomeRMIClientSocketFactory(
                InstanceCommunicationService.IFACE_ADDRS.values().stream().map((address) -> {
                    return address.getHostAddress();
                }).collect(Collectors.toList()).toArray(new String[]{})
        ), null);
        this.in = in;
    }
    
    public static RemoteInputStream wrap(InputStream in) throws RemoteException {
        return new RemoteInputStream(new RemoteInputStreamServer(in));
    }
    
    @Override
    public int available() throws IOException, RemoteException {
        return in.available();
    }

    @Override
    public byte[] read(int count) throws IOException, RemoteException {
        final byte buffer[] = new byte[count];
        final int actualCount = in.read(buffer);
        if (actualCount == count) {
            return buffer;
        } else if (actualCount == -1) {
            return EMPTY_BUFFER;
        } else {
            final byte data[] = new byte[actualCount];
            System.arraycopy(buffer, 0, data, 0, data.length);
            return data;
        }
    }

    @Override
    public void close() throws IOException, RemoteException {
        try {
            in.close();
        } catch (IOException ioex) {
            throw ioex;
        } finally {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}
