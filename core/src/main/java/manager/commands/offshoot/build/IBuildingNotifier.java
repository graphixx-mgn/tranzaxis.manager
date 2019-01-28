package manager.commands.offshoot.build;

import org.radixware.kernel.common.check.RadixProblem;

import javax.swing.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;


public interface IBuildingNotifier extends Remote {

    void error(UUID uuid, Throwable ex) throws RemoteException;
    void event(UUID uuid, RadixProblem.ESeverity severity, String defId, String name, ImageIcon icon, String message) throws RemoteException;
    void progress(UUID uuid, int percent) throws RemoteException;
    void description(UUID uuid, String text) throws RemoteException;
    void isPaused(UUID uuid) throws RemoteException;
    
    interface IBuildListener {

        void error(Throwable ex);
        void event(RadixProblem.ESeverity severity, String defId, String name, ImageIcon icon, String message);
        void progress(int percent);
        void description(String text);
        void isPaused();
        
    }
    
}
