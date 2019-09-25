package codex.log;

import codex.context.IContext;
import codex.context.ServiceCallContext;
import codex.model.Bootstrap;
import codex.utils.ImageUtils;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.spi.LoggingEvent;
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
                .parallel()
                .filter(ctxClass -> ctxClass != LogManagementService.class)
                .map(ctxClass -> {
                    if (ctxClass.isAnonymousClass() && IContext.class.isAssignableFrom(ctxClass.getSuperclass())) {
                        Class<? extends IContext> superClass = ctxClass.getSuperclass().asSubclass(IContext.class);
                        return superClass;
                    } else {
                        return ctxClass;
                    }
                })
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

    private final DatabaseAppender dbAppender = new DatabaseAppender() {
        @Override
        public void append(LoggingEvent event) {
            super.append(event);
            listeners.forEach(listener -> listener.eventAppended(event));
        }
    };
    private final List<IAppendListener> listeners = new LinkedList<>();
    
    Logger(String name) {
        super(name);
        AsyncAppender asyncAppender = new AsyncAppender() {
            @Override
            public synchronized void doAppend(LoggingEvent event) {
                String contexts = Logger.getMessageContexts().stream()
                        .map(Logger::getContextId)
                        .collect(Collectors.joining(","));
                super.doAppend(new LoggingEvent(
                        event.getFQNOfLoggerClass(),
                        event.getLogger(),
                        event.getTimeStamp(),
                        event.getLevel(),
                        event.getMessage().toString().replaceAll("\"", "'"),
                        event.getThreadName(),
                        event.getThrowableInformation(),
                        contexts,
                        event.getLocationInformation(),
                        null
                ));
            }
        };
        asyncAppender.addAppender(dbAppender);
        Logger.getRootLogger().addAppender(asyncAppender);
    }

    public static ILogManagementService getLogger() {
        return LMS;
    }

    void addAppendListener(IAppendListener listener){
        listeners.add(listener);
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
                .map(ctxClass -> {
                    if (ctxClass.isAnonymousClass() && IContext.class.isAssignableFrom(ctxClass.getSuperclass())) {
                        Class<? extends IContext> superClass = ctxClass.getSuperclass().asSubclass(IContext.class);
                        return superClass;
                    } else {
                        return ctxClass;
                    }
                })
                .distinct()
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


    @FunctionalInterface
    interface IAppendListener {
        void eventAppended(LoggingEvent event);
    }
}
