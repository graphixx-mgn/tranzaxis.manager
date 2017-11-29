package codex.component.render;

import codex.component.button.IButton;
import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.model.Entity;
import codex.type.Bool;
import codex.type.Enum;
import codex.type.Iconified;
import codex.type.StringList;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import static javax.swing.SwingConstants.CENTER;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;

/**
 * Стандартный рендерер ячеек списка, таблицы и элементов дерева.
 */
public final class GeneralRenderer extends JLabel implements ListCellRenderer, TableCellRenderer, TreeCellRenderer {
    
    public GeneralRenderer() {
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
        setFont(new Font(IEditor.FONT_VALUE.getName(), Font.PLAIN, (int) (IEditor.FONT_VALUE.getSize()*1.2)));
        setBackground(isSelected ? IButton.PRESS_COLOR : list.getBackground());
        if (Iconified.class.isAssignableFrom(value.getClass())) {
            setBorder(new EmptyBorder(1, 4, 1, 2));
            setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 17, 17));
        } else {
            setIcon(null);
        }
        setForeground(value instanceof AbstractEditor.NullValue ? Color.GRAY : IEditor.COLOR_NORMAL);
        return this;
    }

    /**
     * Метод задает внешний вид ячеек таблицы. Вызывается для каждой ячейки при 
     * перерисовке. 
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
        Class columnClass = table.getColumnClass(column);
        CellRenderer cellBox;
        if (row == TableModelEvent.HEADER_ROW) {
            TableHeaderRenderer cellHead = TableHeaderRenderer.getInstance();
            cellHead.setValue((String) value);
            cellHead.setBorder(new CompoundBorder(
                    new MatteBorder(0, column == 0 ? 0 : 1, 1, 0, Color.GRAY),
                    new EmptyBorder(1, 6, 0, 0)
            ));
            return cellHead;
            
        } else {
            if (Bool.class.equals(columnClass)) {
                cellBox = BoolCellRenderer.getInstance();
            } else {
                cellBox = ComplexCellRenderer.getInstance();
            }
            cellBox.setValue(value);
            cellBox.setBackground(isSelected ? IButton.PRESS_COLOR : table.getBackground());
            cellBox.setBorder(new CompoundBorder(
                    new MatteBorder(0, column == 0 ? 0 : 1, 1, 0, Color.LIGHT_GRAY), 
                    new EmptyBorder(1, 6, 0, 0)
            ));
            return cellBox;
        }
    }

    /**
     * Метод задает внешний вид элементов дерева. Вызывается для каждого элемента
     * при перерисовке виджета.
     * @param tree Виджет-дерево.
     * @param value Ссылка на узел дерева. 
     * @param selected Признак того что узел дерева выделен.
     * @param expanded Признак того что узел дерева развернут.
     * @param leaf Признак что указанный узел не имеет потомков.
     * @param row Сквозной индекс узла в дереве.
     * @param hasFocus Признак того что узел имеет фокус.
     */
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Entity entity = (Entity) value;
        int iconSize = tree.getRowHeight()-2;
        ImageIcon icon;
        if (!entity.model.isValid()) {
            icon = ImageUtils.resize(ImageUtils.combine(
                entity.getIcon(),
                ImageUtils.getByPath("/images/warn.png")    
            ), iconSize, iconSize);
        } else {
            icon = ImageUtils.resize(entity.getIcon(), iconSize, iconSize);
        }
        setDisabledIcon(ImageUtils.grayscale(icon));
        setIcon(icon);
        setText(entity.toString());
        
        setForeground(selected ? Color.WHITE : IEditor.COLOR_NORMAL);
        setBackground(selected ? Color.decode("#55AAFF") : Color.WHITE);
        setBorder(new EmptyBorder(15, 2, 15, 7));
        setEnabled((entity.getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED);
        return this;
    }
    
}
