package codex.log.html;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class HTMLFileAppender extends AppenderSkeleton {
    
    private String           fileName;
    private FileOutputStream fileStream;
    
    public String getFile() {
        return fileName;
    }
    
    public void setFile(String file) throws IOException {
        this.fileName = file;
//        if (Files.exists(Paths.get(fileName))) {
//            fileStream = new FileOutputStream(fileName, true);
//        } else {
            Files.createDirectories(Paths.get(fileName).getParent());
            fileStream = new FileOutputStream(fileName, false);
            fileStream.write(prepareHtml().getBytes());
//        }
        fileStream.write(getLayout().getHeader().getBytes());
        fileStream.flush();
    }

    @Override
    protected void append(LoggingEvent event) {
        try {
            fileStream.write(getLayout().format(event).getBytes());
        } catch (IOException e) {
        }
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
        return true;
    }
    
    private String prepareHtml() throws IOException {
        return new StringBuffer()
                .append("<html>\n")
                .append(prepareHead())
                .append(prepareBody())
                .toString();
    }
    
    private String prepareHead() throws IOException {
        return new StringBuffer()
                .append("\t<head>\n")
                .append(prepareCSS("/resource/log.css"))
                .append(prepareJavaScript("/resource/jquery-3.2.1.min.js"))
                .append(prepareJavaScript("/resource/log.js"))
                .append("\t</head>\n")
                .toString();
    }
    
    private String prepareCSS(String resource) throws IOException {
        final InputStream cssResource = this.getClass().getResourceAsStream(resource);
        return new StringBuffer()
                .append("\t\t<style>\n")
                .append("\t\t\t")
                .append(new Scanner(cssResource, "UTF-8").useDelimiter("\\A").next().replaceAll("\n", "\n\t\t\t"))
                .append("\n")
                .append("\t\t</style>\n")
                .toString();
    }
    
    private String prepareJavaScript(String resource) throws IOException {
        final InputStream jsResource = this.getClass().getResourceAsStream(resource);
        return new StringBuffer()
                .append("\t\t<script type=\"text/javascript\">\n")
                .append("\t\t\t")
                .append(new Scanner(jsResource, "UTF-8").useDelimiter("\\A").next().replaceAll("\n", "\n\t\t\t"))
                .append("\n")
                .append("\t\t</script>\n")
                .toString();
    }
    
    private String prepareBody() {
        return new StringBuffer()
                .append("\t<body bgcolor=\"#FFFFFF\" topmargin=\"6\" leftmargin=\"6\">\n")
                .append(preparePanel())
                .toString();
    }
    
    private String preparePanel() {
        return new StringBuilder()
                .append("\t\t<div class=\"controls\">\n")
                .append(prepareFilterBar())
                .append("\t\t</div>\n")
                .toString();
    }
    
    private String prepareFilterBar() {
        return new StringBuffer()
                .append("<div id=\"levels\" class=\"toolbar\">")
                .append("<table><tr>")
                .append("<td>Filters:<td/>")
                .append("<td><input type=\"checkbox\" class=\"switch\" data=\"DEBUG\" onclick=\"applyFilters();\"></td>")
                .append("<td><div class=\"icon DEBUG\"></div>Debug</td>")
                .append("<td><input type=\"checkbox\" class=\"switch\" data=\"INFO\" onclick=\"applyFilters();\" checked=\"checked\"></td>")
                .append("<td><div class=\"icon INFO\"></div>Info</td>")
                .append("<td><input type=\"checkbox\" class=\"switch\" data=\"WARN\" onclick=\"applyFilters();\" checked=\"checked\"></td>")
                .append("<td><div class=\"icon WARN\"></div>Warning</td>")
                .append("<td><input type=\"checkbox\" class=\"switch\" data=\"ERROR\" onclick=\"applyFilters();\" checked=\"checked\"></td>")
                .append("<td><div class=\"icon ERROR\"></div>Error</td>")
                .append("</tr></table>")
                .append("</div>")
                .toString();
    }

}
