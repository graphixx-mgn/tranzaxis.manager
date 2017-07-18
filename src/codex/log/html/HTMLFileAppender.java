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
    
    /**
     * Get file defined in the .properties file
     * @return File path
     */
    public String getFile() {
        return fileName;
    }
    
    /**
     * Set output file to write logs.
     * @param file File path
     * @throws IOException 
     */
    public void setFile(String file) throws IOException {
        this.fileName = file;
        if (Files.exists(Paths.get(fileName))) {
            fileStream = new FileOutputStream(fileName, true);
        } else {
            Files.createDirectories(Paths.get(fileName).getParent());
            fileStream = new FileOutputStream(fileName, false);
            fileStream.write(prepareHtml().getBytes());
        }
        fileStream.write(getLayout().getHeader().getBytes());
        fileStream.flush();
    }

    /**
     * Method is being called while new log event exists.
     * @param event Log record data
     */
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
    
    /**
     * Prepares <html> part of file with all subparts 
     * @return String of HTML code
     * @throws IOException 
     */
    private String prepareHtml() throws IOException {
        return new StringBuffer()
                .append("<html>\n")
                .append(prepareHead())
                .append(prepareBody())
                .toString();
    }
    
    /**
     * Prepares <head> part of file with all subparts such as CSS, JavaScript.
     * @return String of HTML code
     * @throws IOException 
     */
    private String prepareHead() throws IOException {
        return new StringBuffer()
                .append("<head>\n")
                .append(prepareCSS("/resource/log.css"))
                .append(prepareJavaScript("/resource/jquery-3.2.1.min.js"))
                .append(prepareJavaScript("/resource/log.js"))
                .append("</head>\n")
                .toString();
    }
    
    /**
     * Prepares CSS injection from resource.
     * @param resource Path to project resource
     * @return String of HTML code
     * @throws IOException 
     */
    private String prepareCSS(String resource) throws IOException {
        final InputStream cssResource = this.getClass().getResourceAsStream(resource);
        return new StringBuffer()
                .append("\t<style>\n")
                .append("\t\t")
                .append(new Scanner(cssResource, "UTF-8").useDelimiter("\\A").next().replaceAll("\n", "\n\t\t"))
                .append("\n")
                .append("\t</style>\n")
                .toString();
    }
    
    /**
     * Prepares JavaScript injection from resource.
     * @param resource Path to project resource
     * @return String of HTML code
     * @throws IOException 
     */
    private String prepareJavaScript(String resource) throws IOException {
        final InputStream jsResource = this.getClass().getResourceAsStream(resource);
        return new StringBuffer()
                .append("\t<script type='text/javascript'>\n")
                .append("\t\t")
                .append(new Scanner(jsResource, "UTF-8").useDelimiter("\\A").next().replaceAll("\n", "\n\t\t"))
                .append("\n")
                .append("\t</script>\n")
                .toString();
    }
    
    /**
     * Prepares <body> part of file with all subparts 
     * @return c
     */
    private String prepareBody() {
        return new StringBuffer()
                .append("<body>\n")
                .append(preparePanel())
                .append(prepareContainer())
                .toString();
    }
    
    /**
     * Prepares element which contains controls.
     * @return String of HTML code
     */
    private String preparePanel() {
        return new StringBuilder()
                .append("\t<div class='nav'>\n")
                .append("\t\t<div class='controls'>\n")
                .append("\t\t\t<div id='levels' class='toolbar'>\n")
                .append("\t\t\t\t<p>Filters:</p>\n")
                .append("\t\t\t\t<table>\n")
                .append("\t\t\t\t\t<tr>\n")
                .append("\t\t\t\t\t\t<td class='check' data='DEBUG'><div class='icon DEBUG'></div>Debug</td>\n")
                .append("\t\t\t\t\t\t<td class='check checked' data='INFO'><div class='icon INFO'></div>Info</td>\n")
                .append("\t\t\t\t\t\t<td class='check checked' data='WARN'><div class='icon WARN'></div>Warning</td>\n")
                .append("\t\t\t\t\t\t<td class='check checked' data='ERROR'><div class='icon ERROR'></div>Error</td>\n")
                .append("\t\t\t\t\t</tr>\n")
                .append("\t\t\t\t</table>\n")
                .append("\t\t\t</div>\n")
                .append("\t\t</div>\n")
                .append("\t\t<ol class='tree'>\n")
                .append("\t\t\t<li><label for='today'>Today</label><input type='checkbox' checked disabled id='today'/><ol></ol></li>\n")
                .append("\t\t\t<li><label for='week'>This week</label><input type='checkbox' id='week'/><ol></ol></li>\n")
                .append("\t\t\t<li><label for='archive'>Archive</label><input type='checkbox' id='archive'/><ol></ol></li>\n")
                .append("\t\t</ol>\n")
                .append("\t</div>\n")
                .toString();
    }
    
    /**
     * Prepares element which contains log sessions.
     * @return String of HTML code
     */
    private String prepareContainer() {
        return new StringBuilder()
                .append("\t<div class='content'>\n")
                .append("\t\t<div id='header'>\n")
                .append("\t\t\t<h4></h4>\n")
                .append("\t\t\t<p></p>\n")
                .append("\t\t</div>\n")
                .toString();
    }

}
