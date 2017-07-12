package codex.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

class LineFormatter extends Formatter {
    
    private final SimpleDateFormat format;
    
    private LineFormatter(SimpleDateFormat format) {
        this.format = format;
    }

    @Override
    public final String format(LogRecord record) {
        String message = format
                .format(new Date(record.getMillis()))
                .concat(" [")
                .concat(String.format("%6s", record.getLevel().getName()))
                .concat("] ");
        
        Throwable throwable = record.getThrown();
        if (throwable != null) {
            StringWriter writer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));
            String exceptionAsString = writer.toString();
            message = message.concat(exceptionAsString);
        } else {
            message = message.concat(MessageFormat. format(record.getMessage(), record.getParameters()));
        }
        
        return message;
    }
    
}
