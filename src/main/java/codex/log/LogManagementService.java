package codex.log;

import codex.notification.NotificationService;
import codex.notification.NotifyCondition;
import codex.service.AbstractService;
import codex.service.ServiceRegistry;
import java.util.EnumSet;


public class LogManagementService extends AbstractService<LoggerServiceOptions> implements ILogManagementService {
    
    @Override
    public boolean isStoppable() {
        return false;
    }

    @Override
    public void startService() {
        super.startService();
        ServiceRegistry.getInstance().addRegistryListener(NotificationService.class, (service) -> {
            ((NotificationService) service).registerSource(Logger.NS_SOURCE, NotifyCondition.ALWAYS);
        });
    }

    @Override
    public void setLevel(Level minLevel) {
        Logger.setLevel(minLevel);
        EnumSet.allOf(codex.log.Level.class).stream()
                .filter((level) -> {
                    return LogUnit.getInstance().filterButtons.containsKey(level.getSysLevel());
                }).forEach((level) -> {
                    LogUnit.getInstance().filterButtons.get(level.getSysLevel()).setChecked(level.ordinal() >= minLevel.ordinal());
                });
    }
    
}
