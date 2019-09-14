package codex.log;

import codex.context.IContext;
import codex.notification.*;
import codex.service.AbstractService;
import codex.service.ServiceRegistry;
import java.awt.*;
import java.text.MessageFormat;

@NotifySource(condition = NotifyCondition.ALWAYS)
@IContext.Definition(id = "LMS", name = "Logger", icon = "/images/log.png")
public class LogManagementService extends AbstractService<LoggerServiceOptions> implements ILogManagementService, IContext, Thread.UncaughtExceptionHandler {

    @Override
    public void debug(String message, Object... params) {
        log(Level.Debug, MessageFormat.format(message, params));
    }

    @Override
    public void info(String message, Object... params) {
        log(Level.Info, MessageFormat.format(message, params));
    }

    @Override
    public void warn(String message, Object... params) {
        log(Level.Warn, MessageFormat.format(message, params));
    }

    @Override
    public void warn(String message, Throwable exception) {
        logError(Level.Warn, message, exception);
    }

    @Override
    public void error(String message, Object... params) {
        log(Level.Error, MessageFormat.format(message, params));
        ServiceRegistry.getInstance().lookupService(INotificationService.class).sendMessage(TrayInformer.getInstance(), new Message(
                TrayIcon.MessageType.ERROR,
                Level.Error.toString(), message
        ));
    }

    @Override
    public void error(String message, Throwable exception) {
        logError(Level.Error, message, exception);
        ServiceRegistry.getInstance().lookupService(INotificationService.class).sendMessage(TrayInformer.getInstance(), new Message(
                TrayIcon.MessageType.ERROR,
                Level.Error.toString(), exception.getMessage()
        ));
    }

    @Override
    public void log(Level level, String message) {
        if (Logger.contextAllowed(Logger.getMessageLastContext(), level)) {
            Logger.getSysLogger().log(level.getSysLevel(), message);
        }
    }

    private void logError(Level level, String message, Throwable exception) {
        if (Logger.contextAllowed(Logger.getMessageLastContext(), level)) {
            Logger.getSysLogger().log(level.getSysLevel(), message, exception);
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        error(MessageFormat.format("Unhandled exception in thread ({0})", thread.getName()), exception);
    }
}
