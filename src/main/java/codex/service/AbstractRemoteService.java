package codex.service;

import codex.instance.InstanceCommunicationService;
import codex.instance.MultihomeRMIClientSocketFactory;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.stream.Collectors;

public abstract class AbstractRemoteService extends UnicastRemoteObject {

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

}
