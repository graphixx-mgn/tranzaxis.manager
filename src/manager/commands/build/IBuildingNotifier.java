package manager.commands.build;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface IBuildingNotifier extends Remote {
    
    public void setProgress(String uid, int percent) throws RemoteException;
    public void setStatus(String uid, String text)   throws RemoteException;
    public void failed(String uid, Throwable ex)     throws RemoteException;
    public void finished(String uid)                 throws RemoteException;
    
    public interface IBuildListener {
    
        public void setProgress(int percent);
        public void setStatus(String text);
        public void failed(Throwable ex);
        public void finished();
        
    }
    
}
