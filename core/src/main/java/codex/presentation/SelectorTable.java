package codex.presentation;

import codex.component.editor.GeneralEditor;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.type.Bool;
import codex.type.IComplexType;
import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Виджет таблицы селекторов (презентации и диалога).
 */
public final class SelectorTable extends JTable implements IEditableTable {

    private static final int SCROLL_BAR_SIZE = UIManager.getInt("ScrollBar.width");

    private final List<String> editableProps = new LinkedList<>();

    public SelectorTable(TableModel model) {
        this(model, true);
    }
    
    public SelectorTable(TableModel model, boolean autoResize) {
        super(model);
        setRowHeight(IEditor.FONT_VALUE.getSize() * 2);
        setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0,0));

        setPreferredScrollableViewportSize(new Dimension(getPreferredSize().width, 200));
        setFillsViewportHeight(true);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "none");

        GeneralRenderer renderer = new GeneralRenderer<>();
        setDefaultRenderer(Bool.class, renderer);
        setDefaultRenderer(Long.class, renderer);
        setDefaultRenderer(IComplexType.class, renderer);
        getTableHeader().setDefaultRenderer(renderer);

        setDefaultEditor(Bool.class, new GeneralEditor());
        setDefaultEditor(IComplexType.class, new GeneralEditor());

        if (autoResize) {
            TableColumnAdjuster adjuster = new TableColumnAdjuster(this, 6);
            adjuster.setDynamicAdjustment(true);
            adjuster.adjustColumns();
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        int totalTableWidth = 0;
        for (int column = 0; column < getColumnCount(); column++) {
            TableColumn tableColumn = getColumnModel().getColumn(column);
            int colWidth = tableColumn.getMinWidth();
            int colMaxWidth = tableColumn.getMaxWidth();
            for (int row = 0; row < getRowCount(); row++) {
                TableCellRenderer cellRenderer = getCellRenderer(row, column);
                Component c = prepareRenderer(cellRenderer, row, column);
                int width = c.getPreferredSize().width + getIntercellSpacing().width;
                colWidth = Math.max(colWidth, width);
                if (colWidth >= colMaxWidth) {
                    colWidth = colMaxWidth;
                    break;
                }
            }
            totalTableWidth += colWidth;
        }
        return new Dimension(
                totalTableWidth + 7*getColumnCount() + 20 + (!getScrollableTracksViewportWidth() ? SCROLL_BAR_SIZE : 0),
                super.getPreferredSize().height + (!getScrollableTracksViewportHeight() ? SCROLL_BAR_SIZE : 0)
        );
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getPreferredSize().width < getParent().getWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getPreferredSize().height < getParent().getHeight();
    }

    @Override
    public void setPropertiesEditable(String... propNames) {
        if (propNames != null) {
            editableProps.addAll(Arrays.asList(propNames));
        }
    }

    @Override
    public void setPropertyEditable(String propName) {
        editableProps.add(propName);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        if (getModel() instanceof ISelectorTableModel) {
            return editableProps.contains(((ISelectorTableModel) getModel()).getPropertyForColumn(column));
        } else {
            return false;
        }
    }
}
