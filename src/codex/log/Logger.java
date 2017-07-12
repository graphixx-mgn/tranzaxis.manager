package codex.log;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class Logger {
    
    private static final Logger instance = new Logger();
    
    public static Logger getLogger() {
        return instance;
    }
    
    private       Level         level = Level.ALL;
    private final List<Handler> handlers = new LinkedList<>();
    
    private Logger() {};
    
    public final void setLevel(Level level) {
        this.level = level;
    }
    
    public final void log(Level level, String message) {
        put(new LogRecord(level, message));
    }
    
    public final void log(Level level, String message, Object[] params) {
        LogRecord record = new LogRecord(level, message);
        record.setParameters(params);
        put(record);
    }
    
    public final void log(Level level, Throwable exception) {
        LogRecord record = new LogRecord(level, "");
        record.setThrown(exception);
        put(record);
    }
    
    public void addHandler(Handler handler) {
        handlers.add(handler);
    } 
    
    private void put(LogRecord record) {
        if (record.getLevel().intValue() >= level.intValue()) {
            for (Handler handler : handlers) {
                if (record.getLevel().intValue() >= handler.getLevel().intValue()) {
                    handler.publish(record);
                }
            }
        }
    }
    
}
