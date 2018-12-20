package codex.presentation;

import codex.editor.IEditor;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.TableModel;


/**
 * Виджет таблицы селекторов (презентации и диалога).
 */
public final class SelectorTable extends JTable {
    
    public SelectorTable(TableModel model) {
        super(model);
        setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
        setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0,0));
        
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "none");
    }

}
