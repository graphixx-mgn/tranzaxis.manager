package codex.presentation;

import codex.editor.IEditor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;


/**
 * Виджет таблицы селекторов (презентации и диалога).
 */
public final class SelectorTable extends JTable {
    
    public SelectorTable(TableModel model) {
        super(model);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
        setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0,0));
        setPreferredScrollableViewportSize(getPreferredSize());
        
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "none");
        
        addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
            @Override
            public void ancestorResized(HierarchyEvent e) {
                super.ancestorResized(e);
                adjustColumnWidth();
            }
        });
    }
    
    private void adjustColumnWidth() {
        int cumulativeActual = 0;
        for (int columnIndex = 0; columnIndex < getColumnCount(); columnIndex++) {
            int width = 0;
            TableColumn column = getColumnModel().getColumn(columnIndex);
            
            for (int row = -1; row < getRowCount(); row++) {
                JComponent comp;
                TableCellRenderer renderer;
                if (row == -1) {
                    renderer = getTableHeader().getDefaultRenderer();
                    comp = (JComponent) renderer.getTableCellRendererComponent(
                            this, 
                            getColumnName(columnIndex),
                            false, false, row, columnIndex
                    );
                } else {
                    renderer = getCellRenderer(row, columnIndex);
                    comp = (JComponent) prepareRenderer(renderer, row, columnIndex);
                }
                Insets cellInsets = comp.getInsets();
                width = Math.max(
                        column.getPreferredWidth(),
                        comp.getPreferredSize().width+cellInsets.left+cellInsets.right
                );
                column.setPreferredWidth(width);
            }
            if (columnIndex < getColumnCount() - 1) {
                cumulativeActual += width;
            } else {
                column.setPreferredWidth(
                        getParent().getSize().width-cumulativeActual
                );
            }
        }
    }
    
}
