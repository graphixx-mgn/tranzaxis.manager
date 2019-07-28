package codex.service;

import codex.instance.InstanceCommunicationService;
import codex.instance.MultihomeRMIClientSocketFactory;
import codex.model.Entity;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.stream.Collectors;

public abstract class AbstractRemoteService<P extends RemoteServiceOptions, C extends RemoteServiceControl> extends UnicastRemoteObject implements IRemoteService {

    private P serviceConfig;

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

    public final P getConfiguration() {
        if (serviceConfig == null) {
            try {
                serviceConfig = Entity.newInstance(ServiceCatalog.getServiceConfigClass(
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
