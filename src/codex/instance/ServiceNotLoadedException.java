package codex.instance;

import codex.service.IRemoteService;
import java.rmi.RemoteException;
import java.text.MessageFormat;

public class ServiceNotLoadedException extends Exception {
    
    private final IRemoteService service;
    private final String         reason;
    
    public ServiceNotLoadedException(IRemoteService service) {
        this(service, null);
    }
    
    public ServiceNotLoadedException(IRemoteService service, String reason) {
        this.service = service;
        this.reason = reason;
    }
    
    IRemoteService getService() {
        return service;
    }

    @Override
    public String getMessage() {
        String serviceIdentity;
        try {
            serviceIdentity = service.getTitle();
        } catch (RemoteException e) {
            serviceIdentity = service.getClass().getCanonicalName();
        }
        if (reason == null) {
            return MessageFormat.format("Service ''{0}'' not loaded", serviceIdentity);
        } else {
            return MessageFormat.format("Service ''{0}'' not loaded: {1}", serviceIdentity, reason);
        }
    }
    
}
