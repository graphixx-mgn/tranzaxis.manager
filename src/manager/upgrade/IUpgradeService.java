package manager.upgrade;

import codex.service.IRemoteService;
import java.rmi.RemoteException;
import manager.xml.Version;


public interface IUpgradeService extends IRemoteService {
    
    @Override
    default public String getTitle() throws RemoteException {
        return "Upgrade Service";
    }
    
    public Version getCurrentVersion();
    
}
