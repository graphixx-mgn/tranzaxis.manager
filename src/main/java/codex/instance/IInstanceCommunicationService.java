package codex.instance;

import codex.service.IRemoteService;
import codex.service.IService;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface IInstanceCommunicationService extends IService {

    @Override
    default String getTitle() {
        return "Instance Communication Service";
    }

    Map<String, IRemoteService> getServices() throws RemoteException;
    
    IRemoteService getService(Class clazz) throws RemoteException, NotBoundException;
    
    IRemoteService getService(String className) throws RemoteException, NotBoundException;

    Accessor getAccessor();
    abstract class Accessor {
        /**
         * Возвращяет список подключенных инстанций.
         */
        abstract List<Instance> getInstances();
        /**
         * Добавить слушателя события подключения инстанции.
         */
        abstract void addInstanceListener(IInstanceListener listener);

        /**
         * Удалить слушателя события подключения инстанции.
         */
        abstract void removeInstanceListener(IInstanceListener listener);
    }
}
