package codex.service;

import codex.instance.InstanceCommunicationService;
import codex.instance.MultihomeRMIClientSocketFactory;
import codex.model.Entity;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.stream.Collectors;

public abstract class AbstractRemoteService<T extends RemoteServiceOptions> extends UnicastRemoteObject implements IRemoteService {

    private T serviceConfig;

    protected AbstractRemoteService() throws RemoteException {
        super(0,
                new MultihomeRMIClientSocketFactory(
                    InstanceCommunicationService.IFACE_ADDRS.values().stream()
                            .map(InetAddress::getHostAddress)
                            .collect(Collectors.toList()).toArray(new String[]{})
                ),
                null
        );
    }

    public final T getConfig() {
        if (serviceConfig == null) {
            try {
                serviceConfig = Entity.newInstance(ServiceCatalog.getServiceConfigClass(
                        RemoteServiceOptions.class,
                        AbstractRemoteService.class,
                        getClass()
                ), null, getTitle());
            } catch (RemoteException e) {
                // Must not appear
            }
        }
        return serviceConfig;
    }

}
