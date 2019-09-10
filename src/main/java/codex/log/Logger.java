package codex.log;

import codex.context.IContext;
import codex.context.ServiceCallContext;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

public class Logger extends org.apache.log4j.Logger /*implements Thread.UncaughtExceptionHandler*/ {

    final static ImageIcon DEBUG = ImageUtils.getByPath("/images/debug.png");
    final static ImageIcon INFO  = ImageUtils.getByPath("/images/event.png");
    final static ImageIcon WARN  = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon ERROR = ImageUtils.getByPath("/images/stop.png");
    final static ImageIcon OFF   = ImageUtils.getByPath("/images/unavailable.png");

    private static final LoggerFactory FACTORY = new LoggerFactory();
    private static final LogManagementService LMS = new LogManagementService();

    static {
        Thread.setDefaultUncaughtExceptionHandler(LMS);
        LMS.startService();
    }
    
    Logger(String name) {
        super(name);
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

    private static java.util.List<Class<? extends IContext>> getContexts() {
        return ServiceCallContext.getContextStack().stream()
                .filter(aClass -> !ILogManagementService.class.isAssignableFrom(aClass))
                .collect(Collectors.toList());
    }

    static Class<? extends IContext> getLastContext() {
        List<Class<? extends IContext>> contexts = getContexts();
        return contexts.get(contexts.size()-1);
    }
}
