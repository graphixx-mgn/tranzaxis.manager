package manager.commands.offshoot.build;

import org.radixware.kernel.common.check.RadixProblem;
import javax.swing.*;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IBuildingNotifier extends Remote {

    default void error(Throwable ex) throws RemoteException {}
    default void event(RadixProblem.ESeverity severity, String defId, String name, ImageIcon icon, String message) throws RemoteException {}
    default void progress(int percent) throws RemoteException {}
    default void description(String text) throws RemoteException {}
    void isPaused() throws RemoteException;

}
