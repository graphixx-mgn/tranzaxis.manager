package codex.log;

/* http://alvinalexander.com/java/jwarehouse/jakarta-log4j-1.2.8/examples/subclass */

import codex.notification.INotificationService;
import codex.notification.NotificationService;
import codex.service.ServiceRegistry;
import java.awt.TrayIcon;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.varia.LevelMatchFilter;

/**
 * Extends {@link org.apache.log4j.Logger} in order to support formatted messages.
 * @see Logger#debug
 * @author Gredyaev Ivan
 */
public class Logger extends org.apache.log4j.Logger implements Thread.UncaughtExceptionHandler {
    
    private static final String FQCN = Logger.class.getName();
    private static final LoggerFactory FACTORY = new LoggerFactory();
    final static String  NS_SOURCE = "Logger/Errors";
    
    Logger(String name) {
        super(name);
        Thread.setDefaultUncaughtExceptionHandler(this);
    }
    
    static void setLevel(codex.log.Level minLevel) {
        EnumSet.allOf(codex.log.Level.class).stream()
                .forEach((level) -> {
                    Appender appender = Logger.getRootLogger().getAppender(level.getSysLevel().toString());
                    LevelMatchFilter filter = (LevelMatchFilter) appender.getFilter();
                    while (!filter.getLevelToMatch().equals(level.getSysLevel().toString())) {
                        filter = (LevelMatchFilter) filter.getNext();
                    }
                    filter.setAcceptOnMatch(level.ordinal() >= minLevel.ordinal());
                });
    }
    
    /**
     * This method overrides {@link Logger#getInstance} by supplying its own FACTORY type as a parameter.
     * @param name Name of logger
     */
    public static Category getInstance(String name) {
        return org.apache.log4j.Logger.getLogger(name, FACTORY); 
    }
  
    /**
     * This method overrides {@link Logger#getLogger} by supplying its own FACTORY type as a parameter.
     * @return Instance of {@link Logger}
     */
    public static Logger getLogger() {
        return (Logger) org.apache.log4j.Logger.getLogger(Logger.class.getCanonicalName(), FACTORY); 
    }

    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.debug("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters. 
     * @param params Parameters to fill the template
     */
    public final void debug(String message, Object... params) {
        debug(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.info("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Parameters to fill the template
     */
    public final void info(String message, Object... params) {
        info(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.warn("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Parameters to fill the template
     */
    public final void warn(String message, Object... params) {
        warn(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.error("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Parameters to fill the template
     */
    public final void error(String message, Object... params) {
        super.error(offset(format(message, params)));
        if (ServiceRegistry.getInstance().isServiceRegistered(NotificationService.class))
            ((INotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class))
                    .showMessage(NS_SOURCE, Level.Error.toString(), format(message, params), TrayIcon.MessageType.ERROR);
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.fatal("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Parameters to fill the template
     */
    public final void fatal(String message, Object... params) {
        super.fatal(offset(format(message, params)));
    }
    
    @Override
    public void debug(Object message, Throwable exception) {
        debug(format(((String) message).concat("\n{0}"), new Object[]{stackTraceToString(exception)}));
    }

    @Override
    public void info(Object message, Throwable exception) {
        info(format(((String) message).concat("\n{0}"), new Object[]{stackTraceToString(exception)}));
    }
    
    @Override
    public void warn(Object message, Throwable exception) {
        warn(format(((String) message).concat("\n{0}"), new Object[]{stackTraceToString(exception)}));
    }

    @Override
    public void error(Object message, Throwable exception) {
        super.error(offset(format(((String) message).concat("\n{0}"), new Object[]{stackTraceToString(exception)})));
        if (ServiceRegistry.getInstance().isServiceRegistered(NotificationService.class))
            ((INotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class))
                    .showMessage(NS_SOURCE, Level.Error.toString(), message.toString(), TrayIcon.MessageType.ERROR);
    }
    
    @Override
    public void fatal(Object message, Throwable exception) {
        super.fatal(offset(format(((String) message).concat("\n{0}"), new Object[]{stackTraceToString(exception)})));
    }

    @Override
    public void debug(Object message) {
        super.debug(offset(message.toString()));
    }
    
    @Override
    public void info(Object message) {
        super.info(offset(message.toString()));
    }
    
    @Override
    public void warn(Object message) {
        super.warn(offset(message.toString()));
    }
    
    @Override
    public void error(Object message) {
        super.error(offset(message.toString()));
        if (ServiceRegistry.getInstance().isServiceRegistered(NotificationService.class))
            ((INotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class))
                    .showMessage(NS_SOURCE, Level.Error.toString(), message.toString(), TrayIcon.MessageType.ERROR);
    }

    @Override
    public void fatal(Object message) {
        super.fatal(offset(message.toString()));
        if (ServiceRegistry.getInstance().isServiceRegistered(NotificationService.class))
            ((INotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class))
                    .showMessage(NS_SOURCE, Level.Error.toString(), message.toString(), TrayIcon.MessageType.ERROR);
    }
    
    public static String stackTraceToString(Throwable exception) {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return exception.toString().concat("\n\t").concat(sw.toString());
    }
    
    private String offset(String message) {
        long lines = message.chars().filter(x -> x == '\n').count() + 1;
        if (lines > 1) {
            message = message.replaceAll("\n", "\n                     ");
        }
        return message;
    }
    
    /**
     * Applies parameters to message template
     * @param message Template
     * @param params Array of parameters to fill the template
     * @return Result message
     */
    private String format(String message, Object[] params) {
        return MessageFormat.format(message, params);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        error(MessageFormat.format("Unhandled exception in thread ({0})", thread.getName()), e);
    }
    
}
