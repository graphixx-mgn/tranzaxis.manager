package codex.instance;

import codex.service.IRemoteService;
import codex.service.IService;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;


public interface IInstanceCommunicationService extends IService {

    @Override
    default String getTitle() {
        return "Instance Communication Service";
    }
    
    public IRemoteService getService(Class clazz) throws RemoteException, NotBoundException;
    
    public IRemoteService getService(String className) throws RemoteException, NotBoundException;
    
}
