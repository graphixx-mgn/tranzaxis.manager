package codex.component.render;

import codex.editor.IEditor;
import codex.presentation.SelectorTableModel;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Реализация рендерера заголовка {@link SelectorTableModel}.
 */
final class TableHeaderRenderer extends JLabel implements ICellRenderer<String> {

    private final static TableHeaderRenderer INSTANCE = new TableHeaderRenderer();
    
    public final static TableHeaderRenderer getInstance() {
        return INSTANCE;
    }

    private TableHeaderRenderer() {
        super();
        setOpaque(true);
        setFont(IEditor.FONT_BOLD);
        setForeground(IEditor.COLOR_NORMAL);
        setBackground(Color.decode("#CCCCCC"));
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public void setValue(String value, String placeholder) {
        setText(value);
    }
    
}
