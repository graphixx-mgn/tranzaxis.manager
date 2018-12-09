package codex.service;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;


public interface IRemoteService extends Remote, Serializable {
    
    default String getTitle() throws RemoteException {
        return "Remote service instance ["+getClass().getCanonicalName()+"]";
    };
    
}
