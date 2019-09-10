package codex.log;

import codex.context.IContext;
import codex.context.RootContext;
import org.apache.log4j.spi.LoggingEvent;
import java.text.MessageFormat;

public class ConsoleAppender extends org.apache.log4j.ConsoleAppender {

    @Override
    public synchronized void doAppend(LoggingEvent event) {
        if (event.getLevel().isGreaterOrEqual(getThreshold())) {
            Class<? extends IContext> lastContext = Logger.getLastContext();
            super.doAppend(new LoggingEvent(
                    event.getFQNOfLoggerClass(),
                    event.getLogger(),
                    event.getTimeStamp(),
                    event.getLevel(),
                    offset(event.getMessage().toString()),
                    event.getThreadName(),
                    event.getThrowableInformation(),
                    lastContext.equals(RootContext.class) ? "" : MessageFormat.format(
                            "({0})",
                            lastContext.getAnnotation(IContext.Definition.class).id()
                    ),
                    event.getLocationInformation(),
                    null
            ));
        }
    }

    private String offset(String message) {
        long lines = message.chars().filter(x -> x == '\n').count() + 1;
        if (lines > 1) {
            message = message.replaceAll("\n", "\n                               ");
        }
        return message;
    }
}
