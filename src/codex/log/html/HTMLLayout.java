package codex.log.html;

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
        String level = event.getLevel().toString();
        return new StringBuffer()
                .append("\t\t\t<tr class=\"").append(level).append("\">")
                .append("<td><div class=\"icon ").append(level).append("\"></div>").append("</td>")
                .append("<td>").append(format.format(event.getTimeStamp())).append("</td>")
                .append("<td>").append(prepareMessage(event)).append("</td>")
                .append("</tr>\n")
                .toString();
    }
    
    private String prepareMessage(LoggingEvent event) {
        ThrowableInformation exceptionInfo = event.getThrowableInformation();
        if (exceptionInfo != null) {
            StringWriter writer = new StringWriter();
            exceptionInfo.getThrowable().printStackTrace(new PrintWriter(writer));
            String exceptionAsString = writer
                    .toString()
                    .replaceAll("\\n", "<br/>")
                    .replaceAll("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                    .replaceAll("\\s", "&nbsp;");
            
            return "<span class=\"msg\">"+event.getRenderedMessage()+" ...</span><a class=\"show\">Show details</a>"+
                   "<div class=\"details\">"+exceptionAsString+"</div>";
        } else {
            String message = escape(event.getRenderedMessage())
                    .replaceAll("\\n", "<br/>")
                    .replaceAll("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                    .replaceAll("\\s", "&nbsp;");
            
            if (message.contains("<br/>")) {
                return "<span class=\"msg\">"+message.substring(0, message.indexOf("<br/>"))+" ...</span><a class=\"show\">Show details</a>"+
                       "<div class=\"details\">"+message+"</div>";
            } else {
                return "<span class=\"msg\">"+message+"</span>";
            }
        }
    }

    @Override
    public String getHeader() {
        return new StringBuffer()
                .append("\t\t</table><hr size=\"1\" noshade sessionId=\"").append(sessionId).append("\">\n")
                .append("\t\t<H4>Log session start time: ").append(new Date().toString()).append("</H4>\n")
                .append("\t\t<table cellspacing=\"0\" cellpadding=\"4\" border=\"1\">\n")
                .append("\t\t\t<tr><th width=\"1%\"></th><th width=\"1%\">Time</th><th width=\"100%\">Message</th></tr>\n")
                .toString();
    }
    
    private static String escape(String s) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
                out.append("&#");
                out.append((int) c);
                out.append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
    
}
