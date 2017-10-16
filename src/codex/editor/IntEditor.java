package codex.editor;

import codex.property.PropertyHolder;
import codex.type.Int;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Редактор свойств типа {@link Int}, представляет собой поле ввода.
 */
public class IntEditor extends StrEditor {
    
    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public IntEditor(PropertyHolder propHolder) {
        super(propHolder, (text) -> {
            try {
                return text.isEmpty() || Integer.valueOf(text) <= Integer.MAX_VALUE;
            } catch (NumberFormatException e) {
                return false;
            }
        }, Integer::valueOf);
        
        textField.addKeyListener(new KeyAdapter() {
            
            @Override
            public void keyTyped(KeyEvent event) {
                char c = event.getKeyChar();
                if (!((c >= '0') && (c <= '9') ||
                     (c == KeyEvent.VK_BACK_SPACE) ||
                     (c == KeyEvent.VK_DELETE))) {
                        event.consume();
                }
            }
        });
    }

}
