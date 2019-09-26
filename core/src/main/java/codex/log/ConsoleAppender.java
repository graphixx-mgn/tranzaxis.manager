package codex.log;

import codex.context.IContext;
import codex.context.RootContext;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import java.text.MessageFormat;

public class ConsoleAppender extends org.apache.log4j.ConsoleAppender {

    @Override
    public synchronized void doAppend(LoggingEvent event) {
        if (event.getLevel().isGreaterOrEqual(getThreshold())) {
            Class<? extends IContext> lastContext = Logger.getMessageLastContext();
            ThrowableInformation throwableInfo = event.getThrowableInformation();
            String stacktrace = throwableInfo == null ? null : Logger.stackTraceToString(throwableInfo.getThrowable());
            super.doAppend(new LoggingEvent(
                    event.getFQNOfLoggerClass(),
                    event.getLogger(),
                    event.getTimeStamp(),
                    event.getLevel(),
                    offset(MessageFormat.format(
                            throwableInfo == null ? "{0}" : "{0}\n{1}",
                            event.getMessage(),
                            stacktrace
                    )),
                    event.getThreadName(),
                    null,
                    lastContext.equals(RootContext.class) ? "" : MessageFormat.format(
                            "({0})",
                            Logger.getContextRegistry().getContext(lastContext).getId()
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
