package codex.log;

import codex.context.IContext;
import codex.model.Bootstrap;
import codex.notification.*;
import codex.service.AbstractService;
import codex.service.ServiceRegistry;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

@NotifySource(condition = NotifyCondition.ALWAYS)
@IContext.Definition(id = "LMS", name = "Logger", icon = "/images/log.png")
public class LogManagementService extends AbstractService<LoggerServiceOptions> implements ILogManagementService, IContext, Thread.UncaughtExceptionHandler {

    private final static Map<String, Level> contextLevels = new HashMap<>();

    static {
        contextLevels.putAll(getContextLevels());
        Bootstrap.getCatalog(LoggerServiceOptions.class, LogManagementService.class.getTypeName()).addPreferenceChangeListener(event -> {
            contextLevels.putAll(getContextLevels());
        });
    }

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
        log(Level.Warn, message.concat("\n").concat(Logger.stackTraceToString(exception)));
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
        log(Level.Error, message.concat("\n").concat(Logger.stackTraceToString(exception)));
        ServiceRegistry.getInstance().lookupService(INotificationService.class).sendMessage(TrayInformer.getInstance(), new Message(
                TrayIcon.MessageType.ERROR,
                Level.Error.toString(), exception.getMessage()
        ));
    }

    @Override
    public void log(Level level, String message) {
        if (checkPermission(Logger.getLastContext(), level)) {
            Logger.getSysLogger().log(level.getSysLevel(), message);
        }
    }

    public static boolean checkPermission(Class<? extends IContext> contextClass, Level level) {
        boolean isOption = contextClass.getAnnotation(LoggingSource.class).debugOption();
        boolean ctxAllow = level.getSysLevel().isGreaterOrEqual(contextLevels.get(contextClass.getTypeName()).getSysLevel());
        if (isOption) {
            Class<? extends IContext> parentContextClass = contextClass.getAnnotation(IContext.Definition.class).parent();
            boolean parentCtxAllow = level.getSysLevel().isGreaterOrEqual(contextLevels.get(parentContextClass.getTypeName()).getSysLevel());
            return ctxAllow && parentCtxAllow;
        } else {
            return ctxAllow;
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        error(MessageFormat.format("Unhandled exception in thread ({0})", thread.getName()), exception);
    }

    private static Map<String, Level> getContextLevels() {
        String levelsAsString = Bootstrap.getProperty(
                LoggerServiceOptions.class,
                LogManagementService.class.getTypeName(),
                LoggerServiceOptions.PROP_LOG_LEVELS
        );
        return Arrays.stream(levelsAsString.replaceAll("^\\{(.*)\\}$", "$1").split(", ", -1))
                .map(pair -> pair.split("="))
                .collect(Collectors.toMap(
                        pair -> pair[0],
                        pair -> Level.valueOf(pair[1])
                ));
    }
}
