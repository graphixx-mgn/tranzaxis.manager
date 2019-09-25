package manager.upgrade;

import codex.service.IRemoteService;
import java.rmi.RemoteException;
import manager.upgrade.stream.RemoteInputStream;
import manager.xml.Version;
import manager.xml.VersionsDocument;


public interface IUpgradeService extends IRemoteService {
    
    @Override
    default public String getTitle() throws RemoteException {
        return "Upgrade Service";
    }
    
    public Version getCurrentVersion() throws RemoteException;
    
    public VersionsDocument getDiffVersions(Version from, Version to) throws RemoteException;
    
    public RemoteInputStream getUpgradeFileStream() throws RemoteException;
    
    public String getUpgradeFileChecksum() throws RemoteException;
    
}
