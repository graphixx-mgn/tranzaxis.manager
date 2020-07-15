package codex.log;

import codex.context.IContext;
import codex.context.RootContext;
import codex.context.ServiceCallContext;
import codex.model.PolyMorph;
import codex.service.Service;
import codex.service.ServiceRegistry;
import codex.utils.ImageUtils;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Logger extends org.apache.log4j.Logger {

    // Контексты
    @LoggingSource()
    @IContext.Definition(id = "TODO", name = "TODO notification", icon = "/images/lamp.png", parent = LogManagementService.class)
    private static class TodoContext implements IContext {}

    final static ImageIcon DEBUG = ImageUtils.getByPath("/images/debug.png");
    final static ImageIcon INFO  = ImageUtils.getByPath("/images/event.png");
    final static ImageIcon WARN  = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon ERROR = ImageUtils.getByPath("/images/stop.png");
    final static ImageIcon OFF   = ImageUtils.getByPath("/images/unavailable.png");

    private static final ThreadLocal<Class <? extends IContext>> CALL_CONTEXT = new ThreadLocal<>();
    private static final LoggerFactory FACTORY = new LoggerFactory();
    private static final LogManagementService LMS = new LogManagementService();

    private static final long sessionStartTimestamp = System.currentTimeMillis();
    private static final ContextRegistry CONTEXT_REGISTRY = new ContextRegistry();

    static {
        Thread.setDefaultUncaughtExceptionHandler(LMS);
        ServiceRegistry.getInstance().registerService(LMS);
    }

    public static Class <? extends IContext> getCallContext() {
        return CALL_CONTEXT.get() != null ? CALL_CONTEXT.get() : RootContext.class;
    }

    private static void setCallContext(Class <? extends IContext> rootContext) {
        CALL_CONTEXT.set(rootContext);
    }

    private final List<IAppendListener> listeners = new LinkedList<>();
    
    Logger(String name) {
        super(name);
        final Map<String, String> properties = new HashMap<>();
        AsyncAppender asyncAppender = new AsyncAppender() {
            @Override
            public synchronized void doAppend(LoggingEvent event) {
                String contexts = Logger.getMessageContexts().stream()
                        .map(Class::getTypeName)
                        .collect(Collectors.joining(","));
                String context = Logger.getMessageLastContext().getTypeName();

                properties.put("ctx", context);
                properties.put("ctxlist", contexts);

                super.doAppend(new LoggingEvent(
                        event.getFQNOfLoggerClass(),
                        event.getLogger(),
                        event.getTimeStamp(),
                        event.getLevel(),
                        event.getMessage().toString().replaceAll("\"", "'"),
                        event.getThreadName(),
                        event.getThrowableInformation(),
                        null,
                        event.getLocationInformation(),
                        properties
                ));
            }
        };
        asyncAppender.addAppender(new DatabaseAppender() {
            @Override
            public void append(LoggingEvent event) {
                super.append(event);
                new LinkedList<>(listeners).forEach(listener -> listener.eventAppended(event));
            }
        });
        Logger.getRootLogger().addAppender(asyncAppender);
    }

    public static org.apache.log4j.Logger getLogger(String name) {
        return org.apache.log4j.Logger.getLogger(name, FACTORY);
    }

    public static ILogManagementService getContextLogger(Class <? extends IContext> rootContext) {
        InvocationHandler handler = (proxy, method, args) -> {
            try {
                setCallContext(rootContext);
                return method.invoke(getLogger(), args);
            } finally {
                setCallContext(null);
            }
        };
        Object proxy = Proxy.newProxyInstance(
                ILogManagementService.class.getClassLoader(),
                new Class<?>[] {ILogManagementService.class},
                handler
        );
        return (ILogManagementService) proxy;
    }

    public static ILogManagementService getLogger() {
        return LMS;
    }

    public static ContextRegistry getContextRegistry() {
        return CONTEXT_REGISTRY;
    }

    public static void todo(String message) {
        Exception exception = new Exception();
        Logger.getContextLogger(TodoContext.class).warn(MessageFormat.format("{0}\nLocation: {1}", message, exception.getStackTrace()[1]));
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

    public static boolean contextAllowed(List<Class<? extends IContext>> contexts, Level level) {
        for (Class<? extends IContext> ctx : contexts) {
            if (CONTEXT_REGISTRY.getContext(ctx) == null) continue;
            if (!contextAllowed(ctx, level)) return false;
        }
        return true;
    }

    private static boolean contextAllowed(Class<? extends IContext> contextClass, Level level) {
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
        Class<? extends IContext> targetClass;
        if (PolyMorph.class.isAssignableFrom(contextClass)) {
            Class<? extends PolyMorph> rootClass = PolyMorph.getPolymorphClass(contextClass.asSubclass(PolyMorph.class));
            targetClass = IContext.class.isAssignableFrom(rootClass) ? rootClass.asSubclass(IContext.class) : contextClass;
        } else {
            targetClass = contextClass;
        }
        if (targetClass.isAnonymousClass() && IContext.class.isAssignableFrom(targetClass.getSuperclass())) {
            return targetClass.getSuperclass().asSubclass(IContext.class);
        } else {
            return targetClass;
        }
    }

    static java.util.List<Class<? extends IContext>> getMessageContexts() {
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
        private String    name;
        private ImageIcon icon;
        private Level     level;

        private ContextInfo parent;
        private final Class<? extends IContext> clazz;

        private static ContextInfo getInstance(Class<? extends IContext> contextClass) {
            try {
                return new ContextInfo(contextClass);
            } catch (Exception e) {
                return null;
            }
        }

        private ContextInfo(Class<? extends IContext> contextClass) throws Exception {
            Class<? extends IContext.IContextProvider> ctxProvider = contextClass.getAnnotation(LoggingSource.class).ctxProvider();
            IContext.Definition contextDef = ctxProvider.newInstance().getDefinition(contextClass);
            id     = contextDef.id();
            name   = contextDef.name();
            icon   = ImageUtils.getByPath(contextClass, contextDef.icon());
            parent = contextClass != contextDef.parent() ? ContextInfo.getInstance(contextDef.parent()) : null;
            clazz  = contextClass;
            level  = getContextLevel();
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

        public ContextInfo getParent() {
            return parent;
        }

        private Level getContextLevel() {
            String level = Service.getProperty(
                    LogManagementService.class,
                    clazz.getTypeName()
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
        private final static Map<String, ContextInfo> CONTEXTS = new HashMap<>();

        private ContextRegistry() {
            StreamSupport.stream(ClassIndex.getSubclasses(IContext.class).spliterator(),false)
                    .filter(ctxClass -> !(
                            Modifier.isInterface(ctxClass.getModifiers()) ||
                            Modifier.isAbstract(ctxClass.getModifiers())
                    ))
                    .map(Logger::resolveContextClass)
                    .map(ContextInfo::getInstance)
                    .forEach(ctxInfo -> CONTEXTS.putIfAbsent(ctxInfo.clazz.getTypeName(), ctxInfo));
            SwingUtilities.invokeLater(() -> CONTEXTS.values().stream()
                    .sorted(Comparator.comparing(ctxInfo -> ctxInfo.clazz.getTypeName()))
                    .filter(contextInfo -> !contextInfo.getClazz().equals(TodoContext.class))
                    .forEach(ctxInfo -> LMS.getSettings().attachContext(ctxInfo))
            );
        }

        public ContextInfo getContext(Class<? extends IContext> contextClass) {
            return CONTEXTS.get(contextClass.getTypeName());
        }

        public ContextInfo getContext(String className) {
            return CONTEXTS.get(className);
        }

        Collection<ContextInfo> getContexts() {
            return CONTEXTS.values();
        }

        public final void registerContext(Class<? extends IContext> contextClass) {
            ContextInfo ctxInfo = CONTEXTS.containsKey(contextClass.getTypeName()) ?
                    CONTEXTS.get(contextClass.getTypeName()) :
                    ContextInfo.getInstance(contextClass);
            CONTEXTS.put(ctxInfo.clazz.getTypeName(), ctxInfo);
            LMS.getSettings().attachContext(ctxInfo);
        }

        public final void unregisterContext(Class<? extends IContext> contextClass) {
            //TODO: Сохранять класс в БД при удалении чтобы можно было смотреть прошлый лог
            if (CONTEXTS.containsKey(contextClass.getTypeName())) {
                ContextInfo ctxInfo = CONTEXTS.remove(contextClass.getTypeName());
                LMS.getSettings().detachContext(ctxInfo);
            }
        }
    }
}
