package codex.log.layout;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

public class HTMLLayout extends org.apache.log4j.HTMLLayout {
    
    private static final SimpleDateFormat format = new SimpleDateFormat("HH.mm.ss,SSS");
    private final String sessionId;
    
    public HTMLLayout() {
        sessionId = UUID.randomUUID().toString();
    }

    @Override
    public String format(LoggingEvent event) {
        final StringBuilder builder = new StringBuilder()
                .append("<tr sessionId=\"").append(sessionId).append("\">\n")
                .append("<td>").append(format.format(event.getTimeStamp())).append("</td>\n")
                .append("<td>").append(event.getLevel().toString()).append("</td>\n")
                .append("<td>").append(prepareMessage(event)).append("</td>\n")
                .append("</tr>\n")
        ;
        return builder.toString();
    }
    
    private String prepareMessage(LoggingEvent event) {
        ThrowableInformation exceptionInfo = event.getThrowableInformation();
        if (exceptionInfo != null) {
            StringWriter writer = new StringWriter();
            exceptionInfo.getThrowable().printStackTrace(new PrintWriter(writer));
            String exceptionAsString = writer.toString();
            
            return exceptionAsString
                    .replaceAll("\\n", "<br/>")
                    .replaceAll("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                    .replaceAll("\\s", "&nbsp;");
        } else {
            return event.getRenderedMessage();
        }
    }

    @Override
    public String getHeader() {
        final StringBuilder builder = new StringBuilder()
                .append("<html>\n<head>\n<title>").append(getTitle()).append("</title>\n")
                .append("")
                .append("</head>\n")
                .append("<body bgcolor=\"#FFFFFF\" topmargin=\"6\" leftmargin=\"6\">\n")
                //<hr size="1" noshade>
                //Log session start time Thu Jul 13 21:21:37 YEKT 2017<br>
                .append("<hr size=\"1\" noshade>\n")
                .append("Log session start time: ").append(new Date().toString()).append("\n")
                .append("<table cellspacing=\"0\" cellpadding=\"4\" border=\"1\" bordercolor=\"#224466\" width=\"100%\">\n")
                .append("<tr><th>Level</th><th>Time</th><th>Message</th></tr>\n")
        ;
        return builder.toString();
    }
    
}
