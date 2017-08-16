package codex.log;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

public class TextPaneAppender extends AppenderSkeleton {
    
    private final JTextPane pane;
    private final Map<Level, Style> levelStyle = new HashMap<>();
    
    public TextPaneAppender(JTextPane pane) {
        this.pane = pane;
        
        registerStyle(Level.DEBUG, Color.GRAY);
        registerStyle(Level.INFO,  Color.BLACK);
        registerStyle(Level.WARN,  Color.decode("#AA3333"));
        registerStyle(Level.ERROR, Color.decode("#FF3333"));
        
        setLayout(new PatternLayout("%d{ABSOLUTE} [%5p] %m%n"));
    }
    
    private void registerStyle(Level level, Color color) {
        Style style = this.pane.addStyle(level.toString(),  null);
        style.addAttribute("level", level);
        StyleConstants.setForeground(style, color);
        levelStyle.put(level, style);
    }

    @Override
    protected void append(LoggingEvent event) {
        String message = getLayout().format(event);
        if (event.getThrowableStrRep() != null) {
            message += String.join("\n", event.getThrowableStrRep())+"\n";
        }
        try {
            pane.getDocument().insertString(pane.getDocument().getLength(), message, levelStyle.get(event.getLevel()));
        } catch (BadLocationException e) {}   
    }
    
    public final void toggleLevel(Level level, boolean enable) {
        StyledDocument oldDoc = pane.getStyledDocument();
        StyledDocument newDoc = new DefaultStyledDocument();
        StyleConstants.setFontSize(levelStyle.get(level), enable ? pane.getFont().getSize() : 0);
        
        Element element;
        for(int i = 0; i < oldDoc.getLength(); i++) {
            element = oldDoc.getCharacterElement(i);
            AttributeSet       oldAttrs = element.getAttributes();
            SimpleAttributeSet newAttrs = new SimpleAttributeSet(oldAttrs);
            if (oldAttrs.containsAttribute("level", level)) {
                StyleConstants.setFontSize(newAttrs, enable ? pane.getFont().getSize() : 0);
            }
            try {
                newDoc.insertString(newDoc.getLength(), oldDoc.getText(i, 1), newAttrs);
            } catch (BadLocationException ex) {} 
        }
        
        pane.setStyledDocument(newDoc);
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
        return true;
    }

}
