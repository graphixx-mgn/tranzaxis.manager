package codex.log;

import codex.context.IContext;
import codex.context.ServiceCallContext;
import codex.service.LocalServiceOptions;
import codex.utils.ImageUtils;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Logger extends org.apache.log4j.Logger {

    final static ImageIcon DEBUG = ImageUtils.getByPath("/images/debug.png");
    final static ImageIcon INFO  = ImageUtils.getByPath("/images/event.png");
    final static ImageIcon WARN  = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon ERROR = ImageUtils.getByPath("/images/stop.png");
    final static ImageIcon OFF   = ImageUtils.getByPath("/images/unavailable.png");

    static final LoggerFactory FACTORY = new LoggerFactory();
    private static final LogManagementService LMS = new LogManagementService();

    private static final long sessionStartTimestamp = System.currentTimeMillis();
    private static final ContextRegistry CONTEXT_REGISTRY = new ContextRegistry();

    static {
        Thread.setDefaultUncaughtExceptionHandler(LMS);
        LMS.startService();
    }

    private final DatabaseAppender dbAppender = new DatabaseAppender() {
        @Override
        public void append(LoggingEvent event) {
            super.append(event);
            new LinkedList<>(listeners).forEach(listener -> listener.eventAppended(event));
        }
    };
    private final List<IAppendListener> listeners = new LinkedList<>();
    
    Logger(String name) {
        super(name);
        AsyncAppender asyncAppender = new AsyncAppender() {
            @Override
            public synchronized void doAppend(LoggingEvent event) {
                String contexts = Logger.getMessageContexts().stream()
                        .map(ctxClass -> CONTEXT_REGISTRY.getContext(ctxClass).id)
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

    public static org.apache.log4j.Logger getLogger(String name) {
        return org.apache.log4j.Logger.getLogger(name, FACTORY);
    }

    public static ILogManagementService getLogger() {
        return LMS;
    }

    public static ContextRegistry getContextRegistry() {
        return CONTEXT_REGISTRY;
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
        boolean ctxAllow = level.getSysLevel().isGreaterOrEqual(CONTEXT_REGISTRY.getContext(contextClass).level.getSysLevel());
        if (ctxAllow && isOption(contextClass)) {
            return level.getSysLevel().isGreaterOrEqual(CONTEXT_REGISTRY.getContext(getParentContext(contextClass)).level.getSysLevel());
        } else {
            return ctxAllow;
        }
    }

    static long getSessionStartTimestamp() {
        return sessionStartTimestamp;
    }

    static Class<? extends IContext> getMessageLastContext() {
        List<Class<? extends IContext>> contexts = getMessageContexts();
        return contexts.get(contexts.size()-1);
    }

    static boolean isOption(Class<? extends IContext> contextClass) {
        return contextClass.getAnnotation(LoggingSource.class).debugOption();
    }

    static synchronized void setContextLevel(Class<? extends IContext> contextClass, Level level) {
        CONTEXT_REGISTRY.getContext(contextClass).level = level;
    }

    private static Class<? extends IContext> getParentContext(Class<? extends IContext> contextClass) {
        return contextClass.getAnnotation(IContext.Definition.class).parent();
    }

    private static Class<? extends IContext> resolveContextClass(Class<? extends IContext> contextClass) {
        if (contextClass.isAnonymousClass() && IContext.class.isAssignableFrom(contextClass.getSuperclass())) {
            return contextClass.getSuperclass().asSubclass(IContext.class);
        } else {
            return contextClass;
        }
    }

    private static java.util.List<Class<? extends IContext>> getMessageContexts() {
        return ServiceCallContext.getContextStack().stream()
                .filter(aClass -> !ILogManagementService.class.isAssignableFrom(aClass))
                .map(Logger::resolveContextClass)
                .distinct()
                .collect(Collectors.toList());
    }


    @FunctionalInterface
    interface IAppendListener {
        void eventAppended(LoggingEvent event);
    }


    public static class ContextInfo {
        private final String    id;
        private final String    name;
        private final ImageIcon icon;
        private       Level     level;
        private final Class<? extends IContext> clazz;

        private ContextInfo(Class<? extends IContext> contextClass) {
            IContext.Definition contextDef = contextClass.getAnnotation(IContext.Definition.class);

            clazz = contextClass;
            id    = contextDef.id();
            name  = contextDef.name();
            icon  = ImageUtils.getByPath(contextDef.icon());
            level = getContextLevel();
        }

        public String getId() {
            return id;
        }

        public Class<? extends IContext> getClazz() {
            return clazz;
        }

        public String getName() {
            return name;
        }

        public ImageIcon getIcon() {
            return icon;
        }

        public Level getLevel() {
            return level;
        }

        private Level getContextLevel() {
            String level = LocalServiceOptions.getProperty(
                    LogManagementService.class,
                    id
            );
            if (level != null) {
                return Level.valueOf(level);
            } else {
                boolean isOption = clazz.getAnnotation(LoggingSource.class).debugOption();
                return isOption ? Level.Off : Level.Debug;
            }
        }
    }


    public static class ContextRegistry {
        private final Map<String, ContextInfo> idMap = new HashMap<>();
        private final Map<Class<? extends IContext>, ContextInfo> classMap = new LinkedHashMap<>();

        private ContextRegistry() {
            StreamSupport.stream(ClassIndex.getSubclasses(IContext.class).spliterator(),false)
                    .map(Logger::resolveContextClass)
                    .map(ContextInfo::new)
                    .sorted(Comparator.comparing(ctxInfo -> ctxInfo.clazz.getTypeName()))
                    .forEach(ctxInfo -> {
                        idMap.put(ctxInfo.id, ctxInfo);
                        classMap.put(ctxInfo.clazz, ctxInfo);
                    });
        }

        public ContextInfo getContext(Class<? extends IContext> contextClass) {
            return classMap.get(contextClass);
        }

        public ContextInfo getContext(String id) {
            return idMap.get(id);
        }

        public Collection<Class<? extends IContext>> getContexts() {
            return classMap.keySet();
        }
    }
}
