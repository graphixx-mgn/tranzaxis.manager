package codex.log;

import codex.context.IContext;
import codex.context.ServiceCallContext;
import codex.model.Bootstrap;
import codex.utils.ImageUtils;
import org.apache.log4j.jdbc.JDBCAppender;
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Logger extends org.apache.log4j.Logger {

    final static ImageIcon DEBUG = ImageUtils.getByPath("/images/debug.png");
    final static ImageIcon INFO  = ImageUtils.getByPath("/images/event.png");
    final static ImageIcon WARN  = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon ERROR = ImageUtils.getByPath("/images/stop.png");
    final static ImageIcon OFF   = ImageUtils.getByPath("/images/unavailable.png");

    private static final LoggerFactory FACTORY = new LoggerFactory();
    private static final LogManagementService LMS = new LogManagementService();

    private static final long sessionStartTimestamp = System.currentTimeMillis();
    private static final List<Class<? extends IContext>> contextList =
            StreamSupport.stream(ClassIndex.getSubclasses(IContext.class).spliterator(), false)
                .filter(aClass -> aClass != LogManagementService.class)
                .collect(Collectors.toList());
    private static final Map<Class<? extends IContext>, String> contextIds = contextList.stream()
            .collect(Collectors.toMap(
                    ctxClass -> ctxClass,
                    ctxClass -> ctxClass.getAnnotation(IContext.Definition.class).id()
            ));
    private static final Map<Class<? extends IContext>, ImageIcon> contextIcons = contextList.stream()
            .collect(Collectors.toMap(
                    ctxClass -> ctxClass,
                    ctxClass -> ImageUtils.getByPath(ctxClass.getAnnotation(IContext.Definition.class).icon())
            ));
    private static final Map<Class<? extends IContext>, Level> contextLevels = contextList.stream()
                .collect(Collectors.toMap(
                        ctxClass -> ctxClass,
                        ctxClass -> {
                            String level = Bootstrap.getProperty(
                                    LoggerServiceOptions.class,
                                    LMS.getTitle(),
                                    Logger.getContextId(ctxClass).replace(".", "_")
                            );
                            if (level != null) {
                                return Level.valueOf(level);
                            } else {
                                boolean isOption = ctxClass.getAnnotation(LoggingSource.class).debugOption();
                                return isOption ? Level.Off : Level.Debug;
                            }
                        }
                ));

    static {
        Thread.setDefaultUncaughtExceptionHandler(LMS);
        LMS.startService();
    }
    
    Logger(String name) {
        super(name);
        JDBCAppender dbAppender = new DatabaseAppender();
        Logger.getRootLogger().addAppender(dbAppender);
    }

    public static ILogManagementService getLogger() {
        return LMS;
    }

    static Logger getSysLogger() {
        return (Logger) org.apache.log4j.Logger.getLogger(Logger.class.getCanonicalName(), FACTORY);
    }
    
    public static String stackTraceToString(Throwable exception) {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return sw.toString().trim();
    }

    public static boolean contextAllowed(Class<? extends IContext> contextClass, Level level) {
        boolean ctxAllow = level.getSysLevel().isGreaterOrEqual(contextLevels.get(contextClass).getSysLevel());
        if (ctxAllow && isOption(contextClass)) {
            return level.getSysLevel().isGreaterOrEqual(contextLevels.get(getParentContext(contextClass)).getSysLevel());
        } else {
            return ctxAllow;
        }
    }

    static long getSessionStartTimestamp() {
        return sessionStartTimestamp;
    }

    static List<Class<? extends IContext>> getContexts() {
        return new LinkedList<>(contextList);
    }

    static java.util.List<Class<? extends IContext>> getMessageContexts() {
        return ServiceCallContext.getContextStack().stream()
                .filter(aClass -> !ILogManagementService.class.isAssignableFrom(aClass))
                .collect(Collectors.toList());
    }

    static Class<? extends IContext> getMessageLastContext() {
        List<Class<? extends IContext>> contexts = getMessageContexts();
        return contexts.get(contexts.size()-1);
    }

    static String getContextId(Class<? extends IContext> contextClass) {
        return contextIds.get(contextClass);
    }

    static ImageIcon getContextIcon(Class<? extends IContext> contextClass) {
        return contextIcons.get(contextClass);
    }

    public synchronized static Level getContextLevel(Class<? extends IContext> contextClass) {
        return contextLevels.get(contextClass);
    }

    static boolean isOption(Class<? extends IContext> contextClass) {
        return contextClass.getAnnotation(LoggingSource.class).debugOption();
    }

    private static Class<? extends IContext> getParentContext(Class<? extends IContext> contextClass) {
        return contextClass.getAnnotation(IContext.Definition.class).parent();
    }

    static synchronized void setContextLevel(Class<? extends IContext> contextClass, Level level) {
        contextLevels.replace(contextClass, level);
    }
}
