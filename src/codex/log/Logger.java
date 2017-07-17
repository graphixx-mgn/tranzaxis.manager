package codex.log;

/* http://alvinalexander.com/java/jwarehouse/jakarta-log4j-1.2.8/examples/subclass */

import java.text.MessageFormat;
import org.apache.log4j.Category;

public class Logger extends org.apache.log4j.Logger {
    
    private static final String        FQCN = Logger.class.getName();
    private static final LoggerFactory factory = new LoggerFactory();

    public Logger(String name) {
        super(name);
    }
    
    /**
     * This method overrides {@link Logger#getInstance} by supplying its own factory type as a parameter.
     * @param name Name of logger
     * @return
     */
    public static Category getInstance(String name) {
        return org.apache.log4j.Logger.getLogger(name, factory); 
    }
  
    /**
     * This method overrides {@link Logger#getLogger} by supplying its own factory type as a parameter.
     * @return Instance of {@link Logger}
     */
    public static Logger getLogger() {
        return (Logger) org.apache.log4j.Logger.getLogger(Logger.class.getCanonicalName(), factory); 
    }

    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.debug("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters. 
     * @param params Array of parameters to fill the template
     */
    public final void debug(String message, Object[] params) {
        super.debug(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.info("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Array of parameters to fill the template
     */
    public final void info(String message, Object[] params) {
        super.info(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.warn("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Array of parameters to fill the template
     */
    public final void warn(String message, Object[] params) {
        super.warn(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.error("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Array of parameters to fill the template
     */
    public final void error(String message, Object[] params) {
        super.error(format(message, params));
    }
    
    /**
     * Put parametrized message to the log
     * <pre>
     *  logger.fatal("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
     * </pre>
     * @param message Message template that contains numbered placeholders for parameters
     * @param params Array of parameters to fill the template
     */
    public final void fatal(String message, Object[] params) {
        super.fatal(format(message, params));
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
    
}
