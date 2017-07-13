package codex.log;

import java.text.MessageFormat;

public class Logger extends org.apache.log4j.Logger {
    
    private static final Logger logger = new Logger(Logger.getLogger(Logger.class));
    
    final org.apache.log4j.Logger target;
    
    protected Logger(org.apache.log4j.Logger target) {
        super("");
        this.target = target;
    }
    
//    private final org.apache.log4j.Logger target;
//    
//    private static final Logger instance = new Logger();
//    
    public static Logger getLogger() {
        return logger;
    }
//    
//    private Logger() {
//        super("");
//        target = org.apache.log4j.Logger.getLogger(Logger.class);
//    };
//    
//    public final void debug(String message, Object[] params) {
//        target.debug(format(message, params));
//    }
//    
//    public final void info(String message, Object[] params) {
//        target.info(format(message, params));
//    }
//    
//    public final void warn(String message, Object[] params) {
//        target.warn(format(message, params));
//    }
//    
//    public final void error(String message, Object[] params) {
//        target.error(format(message, params));
//    }
//    
//    public final void fatal(String message, Object[] params) {
//        target.fatal(format(message, params));
//    }
//    
//    private String format(String message, Object[] params) {
//        return MessageFormat.format(message, params);
//    }
    
}
