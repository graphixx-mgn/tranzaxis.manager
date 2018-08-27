package codex.component.render;

import codex.component.button.IButton;
import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.presentation.SelectorTableModel;
import codex.property.PropertyState;
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.Enum;
import codex.type.Iconified;
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
    
    private static final ImageIcon ICON_INVALID = ImageUtils.getByPath("/images/warn.png");
    private static final ImageIcon ICON_LOCKED  = ImageUtils.getByPath("/images/lock.png");
    private static final ImageIcon ICON_ERROR   = ImageUtils.getByPath("/images/red.png");
    
    private static Color blend(Color c0, Color c1) {
        double totalAlpha = c0.getAlpha() + c1.getAlpha();
        double weight0 = c0.getAlpha() / totalAlpha;
        double weight1 = c1.getAlpha() / totalAlpha;

        double r = weight0 * c0.getRed() + weight1 * c1.getRed();
        double g = weight0 * c0.getGreen() + weight1 * c1.getGreen();
        double b = weight0 * c0.getBlue() + weight1 * c1.getBlue();
        double a = Math.max(c0.getAlpha(), c1.getAlpha());

        return new Color((int) r, (int) g, (int) b, (int) a);
    }
    
    public GeneralRenderer() {
        setOpaque(true);
        setIconTextGap(6);
        setVerticalAlignment(CENTER);
    }

    /**
     * Метод задает внешний вид ячеек списка. Вызывается для каждой ячейки при 
     * перерисовке виджета.Используется в реализации редакторов типа {@link Enum} 
     * и {@link ArrStr}.
     * @param list Виджет-список.
     * @param value Значение ячейки.
     * @param index Индекс ячейки в списке.
     * @param isSelected Признак того что ячейка выделена.
     * @param hasFocus Признак того что ячейка имеет фокус.
     * @return SWING виджет, являющийся визуальным представлением ячейки.
     */
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {
        if (value != null) {
            setText(value.toString());
            setFont(new Font(IEditor.FONT_VALUE.getName(), Font.PLAIN, (int) (IEditor.FONT_VALUE.getSize()*1.2)));
            setBackground(isSelected ? IButton.PRESS_COLOR : list.getBackground());
            if (Iconified.class.isAssignableFrom(value.getClass())) {
                if (value instanceof Entity && !((Entity) value).model.isValid()) {
                    setIcon(ImageUtils.resize(ImageUtils.combine(
                        ((Entity) value).getIcon(),
                        ICON_INVALID  
                    ), 17, 17));
                } else {
                    setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 17, 17));
                }
            } else {
                setIcon(null);
            }
            setBorder(new EmptyBorder(1, 4, 1, 2));
            setForeground(value instanceof AbstractEditor.NullValue ? Color.GRAY : IEditor.COLOR_NORMAL);
        }
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
                    new MatteBorder(0, 0, 1, column == table.getColumnCount()-1 ? 0 : 1, Color.GRAY),
                    new EmptyBorder(1, 6, 0, 6)
            ));
            return cellHead;
        } else {
            if (Bool.class.equals(columnClass)) {
                cellBox = BoolCellRenderer.getInstance();
            } else {
                cellBox = ComplexCellRenderer.getInstance();
            }
            cellBox.setValue(value);
            
            boolean isEntityInvalid = false;
            boolean isEntityLocked  = false;
            PropertyState propState = PropertyState.Good;
            
            if (table.getModel() instanceof SelectorTableModel) {
                EntityModel model = ((SelectorTableModel) table.getModel()).getEntityAt(row).model;
                
                isEntityInvalid = !((SelectorTableModel) table.getModel()).getEntityAt(row).model.isValid();
                isEntityLocked  = ((SelectorTableModel) table.getModel()).getEntityAt(row).islocked();
                propState = model.getPropState(model.getProperties(Access.Select).get(column));
            }
            cellBox.setEnabled(!isEntityLocked);
            
            switch (propState) {
                case Error: 
                    ((ComplexCellRenderer) cellBox).state.setIcon(ICON_ERROR);
                    break;
                default:
                    ((ComplexCellRenderer) cellBox).state.setIcon(null);
            }
            
            if (isEntityLocked) {
                cellBox.setBackground(Color.decode("#E5E5E5"));
            } else if (isEntityInvalid) {
                cellBox.setBackground(Color.decode("#FFEEEE"));
            } else {
                if (row % 2 == 1) {
                    cellBox.setBackground(table.getBackground());
                } else {
                    cellBox.setBackground(Color.decode("#F5F5F5"));
                }
            }
            
            if (isSelected) {
                cellBox.setBackground(blend(Color.decode("#BBD8FF"), cellBox.getBackground()));
            }
            
            if (isEntityLocked) {
                cellBox.setForeground(IEditor.COLOR_DISABLED);
            } else if (isEntityInvalid && value != null) {
                cellBox.setForeground(Color.RED);
            } else if (value == null) {
                cellBox.setForeground(IEditor.COLOR_DISABLED);
            } else {
                cellBox.setForeground(IEditor.COLOR_NORMAL);
            }
            
            if (column == 0) {
                int iconSize = table.getRowHeight() - 6;
                if (isEntityLocked) {
                    ((ComplexCellRenderer) cellBox).label.setIcon(
                        ImageUtils.resize(ICON_LOCKED, iconSize, iconSize)
                    );
                } else if (isEntityInvalid) {
                    ((ComplexCellRenderer) cellBox).label.setIcon(
                        ImageUtils.resize(ICON_INVALID, iconSize, iconSize)
                    );
                }
            }
            
            ((ComplexCellRenderer) cellBox).label.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, column == table.getColumnCount()-1 ? 0 : 1, Color.LIGHT_GRAY), 
                    new EmptyBorder(1, 6, 0, 6)
            ));
            ((ComplexCellRenderer) cellBox).state.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), 
                    new EmptyBorder(0, 0, 0, 0)
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
        if (entity.islocked()) {
            icon = ImageUtils.resize(ImageUtils.combine(
                entity.getIcon(),
                ICON_LOCKED
            ), iconSize, iconSize);
        } else if (!entity.model.isValid()) {
            icon = ImageUtils.resize(ImageUtils.combine(
                entity.getIcon(),
                ICON_INVALID
            ), iconSize, iconSize);
        } else {
            icon = ImageUtils.resize(entity.getIcon(), iconSize, iconSize);
        }
        JLabel label = new JLabel(entity.toString()) {{
            setOpaque(true);
            setIconTextGap(6);
            setVerticalAlignment(CENTER);
            
            setDisabledIcon(ImageUtils.grayscale(icon));
            setIcon(icon);

            setForeground(selected ? Color.WHITE : IEditor.COLOR_NORMAL);
            setBackground(selected ? Color.decode("#55AAFF") : Color.WHITE);
            setBorder(new EmptyBorder(15, 2, 15, 7));
            setEnabled((entity.getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED); 
        }};
        return label;
    }
    
}
