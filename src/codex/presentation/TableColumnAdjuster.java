package codex.presentation;

import java.awt.Component;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * Корректировщик размера столбцов таблицы.
 */
public final class TableColumnAdjuster {
    
    private static final int MAX_SIZE = 300;
    private static final int MIN_SIZE = 100;
    
    private final JTable table;

    /**
     * Конструктор.
     * @param table Ссылка на таблицу, которую следует корректировать. 
     */
    public TableColumnAdjuster(JTable table) {
        this.table = table;
    }

    /**
     * Корректировка всех столбцов таблицы.
     */
    public void adjustColumns() {
        TableColumnModel tcm = table.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            adjustColumn(i);
        }
    }

    /**
     * Корректировка всех столбца таблицы по его индексу, но в диапазоне 
     * {@link TableColumnAdjuster#MAX_SIZE} - {@link TableColumnAdjuster#MAX_SIZE}.
     */
    public void adjustColumn(final int column) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (!tableColumn.getResizable()) return;

        int columnHeaderWidth = getColumnHeaderWidth( column );
        int columnDataWidth   = getColumnDataWidth( column );
        int preferredWidth    = Math.min(
                MAX_SIZE,
                Math.max(
                        MIN_SIZE,
                        Math.max(columnHeaderWidth, columnDataWidth)
                )
        );
        updateTableColumn(column, preferredWidth);
    }

    /**
     * Расчет ширины ячейки заголовка.
     * @param column Индекс столбца.
     */
    private int getColumnHeaderWidth(int column) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        Object value = tableColumn.getHeaderValue();
        TableCellRenderer renderer = tableColumn.getHeaderRenderer();

        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component c = renderer.getTableCellRendererComponent(table, value, false, false, -1, column);
        return c.getPreferredSize().width;
    }

    /**
     * Расчет ширины столбца на основе наибольшей по рамеру ячейки.
     * @param column Индекс столбца.
     */
    private int getColumnDataWidth(int column) {
        int preferredWidth = 0;
        if (column < table.getColumnCount() - 1) {
            for (int row = 0; row < table.getRowCount(); row++) {
                preferredWidth = Math.max(preferredWidth, getCellDataWidth(row, column));
            }
        } else {
            for (int i = 0; i < table.getColumnCount()-1; i++) {
                TableColumn tableColumn = table.getColumnModel().getColumn(i);
                preferredWidth += tableColumn.getWidth();
            }
            preferredWidth = table.getParent().getSize().width - preferredWidth;
        }
        return preferredWidth;
    }

    /**
     * Расчет ширины ячейки.
     * @param row Индекс строки.
     * @param column Индекс столбца.
     */
    private int getCellDataWidth(int row, int column) {
        TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        Component c = table.prepareRenderer(cellRenderer, row, column);
        Insets cellInsets = ((JComponent) c).getInsets();
        int width = c.getPreferredSize().width + table.getIntercellSpacing().width + cellInsets.left + cellInsets.right;
        return width;
    }

    /**
     * Изменение размера столбца на основе рассчитанного значения.
     * @param column Индекс столбца.
     * @param width Размер в пикселях.
     */
    private void updateTableColumn(int column, int width) {
        final TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (! tableColumn.getResizable()) return;
        table.getTableHeader().setResizingColumn(tableColumn);
        tableColumn.setWidth(width);
    }
    
}