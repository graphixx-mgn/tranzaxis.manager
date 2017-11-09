package codex.component.render;

import codex.component.button.IButton;
import codex.editor.IEditor;
import codex.type.Iconified;
import codex.type.StringList;
import codex.type.Enum;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import static javax.swing.SwingConstants.CENTER;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;

/**
 * Стандартный рендерер ячеек списка и таблицы.
 */
public final class DefaultRenderer extends JLabel implements ListCellRenderer, TableCellRenderer {
    
    public DefaultRenderer() {
        setOpaque(true);
        setIconTextGap(6);
        setVerticalAlignment(CENTER);
    }

    /**
     * Метод задает внешний вид ячеек списка. Вызывается для каждой ячейки при 
     * перерисовке виджета.Используется в реализации редакторов типа {@link Enum} 
     * и {@link StringList}.
     * @param list Виджет-список.
     * @param value Значение ячейки.
     * @param index Индекс ячейки в списке.
     * @param isSelected Признак того что ячейка выделена.
     * @param hasFocus Признак того что ячейка имеет фокус.
     * @return SWING виджет, являющийся визуальным представлением ячейки.
     */
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {
        setText(value.toString());
        setFont(IEditor.FONT_VALUE);
        setBackground(isSelected ? IButton.PRESS_COLOR : list.getBackground());
        if (Iconified.class.isAssignableFrom(value.getClass())) {
            setBorder(new EmptyBorder(1, 4, 1, 2));
            setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 15, 15));
        }
        return this;
    }

    /**
     * Метод задает внешний вид ячеек списка. Вызывается для каждой ячейки при 
     * перерисовке виджета. 
     * @param table Виджет - таблица.
     * @param value Значение ячейки.
     * @param isSelected Признак того что ячейка выделена.
     * @param hasFocus Признак того что ячейка имеет фокус.
     * @param row Индекс строки ячейки в таблице.
     * @param column Индекс колонки ячейки в таблице.
     * @return SWING виджет, являющийся визуальным представлением ячейки.
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setText(value != null ? 
                value.toString() : 
                IEditor.NOT_DEFINED
        );
        setForeground(value != null ? 
                IEditor.COLOR_NORMAL : 
                IEditor.COLOR_DISABLED
        );
        setFont(row == TableModelEvent.HEADER_ROW ? 
                IEditor.FONT_BOLD : 
                IEditor.FONT_VALUE
        );
        setHorizontalAlignment(row == TableModelEvent.HEADER_ROW ? 
                SwingConstants.CENTER : 
                SwingConstants.LEADING
        );
        setBackground(row == TableModelEvent.HEADER_ROW ?
                Color.decode("#CCCCCC") : isSelected ? 
                        IButton.PRESS_COLOR : 
                        table.getBackground()
        );
        setBorder(new CompoundBorder(
                new MatteBorder(
                        0, column == 0 ? 0 : 1, 1, 0, 
                        row == TableModelEvent.HEADER_ROW ? Color.GRAY : Color.LIGHT_GRAY), 
                new EmptyBorder(1, 6, 0, 0)
        ));
        return this;
    }
    
}
