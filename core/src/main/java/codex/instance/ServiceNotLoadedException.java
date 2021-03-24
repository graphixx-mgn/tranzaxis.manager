package codex.instance;

import codex.log.Level;
import codex.service.IRemoteService;
import java.rmi.RemoteException;
import java.text.MessageFormat;

public class ServiceNotLoadedException extends Exception {
    
    private final IRemoteService service;
    private final String         reason;

    private final Level          level;
    
    public ServiceNotLoadedException(IRemoteService service) {
        this(service, null);
    }
    
    public ServiceNotLoadedException(IRemoteService service, String reason) {
        this(service, reason, Level.Error);
    }

    public ServiceNotLoadedException(IRemoteService service, String reason, Level level) {
        this.service = service;
        this.reason = reason;
        this.level = level;
    }

    public final Level getLevel() {
        return level;
    }
    
    IRemoteService getService() {
        return service;
    }

    @Override
    public final String getMessage() {
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
