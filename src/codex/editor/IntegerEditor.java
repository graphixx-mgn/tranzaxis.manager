package codex.editor;

import codex.property.PropertyHolder;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.event.DocumentEvent;

public class IntegerEditor extends StringEditor {
    
    public IntegerEditor(PropertyHolder propHolder) {
        super(propHolder);
        textField.addKeyListener(new KeyAdapter() {
            
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!((c >= '0') && (c <= '9') ||
                     (c == KeyEvent.VK_BACK_SPACE) ||
                     (c == KeyEvent.VK_DELETE))) {
                    e.consume();
                }
            }
        });
    }
    
    @Override
    public void changedUpdate(DocumentEvent event) {
        propHolder.setValue(toInteger(textField.getText()));
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
        propHolder.setValue(toInteger(textField.getText()));
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
        propHolder.setValue(toInteger(textField.getText()));
    }
    
    private Integer toInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        } else {
            return Integer.valueOf(value);
        }
    }
}
