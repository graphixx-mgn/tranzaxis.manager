package codex.log;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

class LineFormatter extends Formatter {
    
    private final SimpleDateFormat format;
    
    LineFormatter(SimpleDateFormat format) {
        this.format = format;
    }

    @Override
    public final String format(LogRecord record) {
        return format
                .format(new Date(record.getMillis()))
                .concat(" [")
                .concat(String.format("%6s", record.getLevel().getName()))
                .concat("] ")
                .concat(MessageFormat.format(record.getMessage(), record.getParameters()));
    }
    
}
