package codex.log;

import java.util.logging.Level;
import java.util.logging.LogRecord;


public class Logger {
    
    private static Logger instance;
    
    private Logger() {};
    
    public static Logger getLogger() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
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
    
    private void put(LogRecord record) {
        System.out.println(record.getMessage());
    }
    
}
