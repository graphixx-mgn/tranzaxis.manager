package codex.component.render;

import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.Access;
import codex.model.Entity;
import codex.presentation.ISelectorTableModel;
import codex.property.PropertyState;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

/**
 * Стандартный рендерер ячеек списка, таблицы и элементов дерева.
 */
public class GeneralRenderer<E> extends JLabel implements ListCellRenderer<E>, TableCellRenderer, TreeCellRenderer {
    
    private static final Boolean DEV_MODE = "1".equals(java.lang.System.getProperty("showHashes"));
    private static final ImageIcon ICON_ERROR = ImageUtils.getByPath("/images/red.png");
    
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
    
    /**
     * Конструктор универсального рендерера.
     */
    public GeneralRenderer() {}

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
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean hasFocus) {
        return new JLabel(
                value.toString().concat(DEV_MODE && !(NullValue.class.isAssignableFrom(value.getClass())) ? ", hash="+value.hashCode() : "")
        ) {{
            setOpaque(true);
            setIconTextGap(6);
            setVerticalAlignment(CENTER);
            setFont(IEditor.FONT_VALUE);

            if (Iconified.class.isAssignableFrom(value.getClass())) {
                ImageIcon icon = ((Iconified) value).getIcon();
                if (icon != null) {
                    icon = ImageUtils.resize(icon, 17, 17);
                    setDisabledIcon(ImageUtils.grayscale(icon));
                    setIcon(icon);
                }
            }
            setBorder(new EmptyBorder(1, 4, 1, 2));
            setForeground(isSelected ? Color.WHITE : (
                NullValue.class.isAssignableFrom(value.getClass()) ? IEditor.COLOR_DISABLED : IEditor.COLOR_NORMAL
            ));
            setBackground(isSelected ?
                    UIManager.getDefaults().getColor("List.selectionBackground") :
                    UIManager.getDefaults().getColor("List.background")
            );
        }};
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
        if (row == TableModelEvent.HEADER_ROW) {
            TableHeaderRenderer cellHead = TableHeaderRenderer.getInstance();
            cellHead.setValue((String) value, null);
            cellHead.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, column == table.getColumnCount()-1 ? 0 : 1, Color.GRAY),
                    new EmptyBorder(1, 6, 0, 6)
            ));
            return cellHead;
        } else {
            CellRenderer cellBox;
            if (Bool.class.equals(columnClass) || (value != null && value.getClass() == Boolean.class)) {
                cellBox = BoolCellRenderer.newInstance();
            } else {
                cellBox = ComplexCellRenderer.newInstance();
            }

            Color bgColor = Color.WHITE;
            Color fgColor = IEditor.COLOR_NORMAL;
            
            boolean isEntityInvalid = false;
            boolean isEntityLocked  = false;
            PropertyState propState = PropertyState.Good;
            
            if (table.getModel() instanceof ISelectorTableModel) {
                ISelectorTableModel selectorModel = (ISelectorTableModel) table.getModel();
                Entity entity = selectorModel.getEntityForRow(row);
                String propName = entity.model.getProperties(Access.Select).get(column);

                isEntityInvalid = !entity.model.isValid();
                isEntityLocked  = entity.islocked();
                propState = entity.model.getPropState(propName);
                
                cellBox.setValue(
                        entity.model.getProperty(propName).isEmpty() ? null : value,
                        entity.model.getProperty(propName).getPlaceholder()
                );
                if (entity.model.getProperty(propName).isEmpty() || (entity.getMode() & INode.MODE_ENABLED) != INode.MODE_ENABLED) {
                    fgColor = Color.decode("#999999");
                }
                if (isEntityInvalid) {
                    bgColor = Color.decode("#FFDDDD");
                    if (!entity.model.getProperty(propName).isEmpty()) {
                        fgColor = Color.decode("#DD0000");
                    }
                } else if (entity.model.getChanges().contains(propName) && table.getModel().isCellEditable(row, column)) {
                    bgColor = Color.decode("#BBFFBB");
                    fgColor = Color.decode("#213200");
                }
            } else {
                cellBox.setValue(value, IEditor.NOT_DEFINED);
            }

            switch (propState) {
                case Error: 
                    cellBox.state.setIcon(ICON_ERROR);
                    break;
                default:
                    cellBox.state.setIcon(null);
            }
            cellBox.setDisabled(isEntityLocked || !table.isEnabled());

            if (cellBox.isDisabled()) {
                bgColor = Color.decode("#E5E5E5");
                fgColor = Color.decode("#999999");
                if (column != 0 && cellBox.getIcon() != null) {
                    cellBox.setIcon(ImageUtils.grayscale((ImageIcon) cellBox.getIcon()));
                }
            }

            if (row % 2 == 0) {
                bgColor = blend(bgColor, Color.decode("#F0F0F0"));
            }
            if (isSelected) {
                bgColor = blend(bgColor, Color.decode("#BBD8FF"));
            }
            cellBox.setForeground(fgColor);
            cellBox.setBackground(bgColor);

            cellBox.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, column == table.getColumnCount()-1 ? 0 : 1, Color.LIGHT_GRAY), 
                    new EmptyBorder(0, 6, 0, 0)
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
        if (tree.getModel() instanceof NodeTreeModel) {
            Entity entity = (Entity) value;
            return new JLabel(entity.toString().concat(DEV_MODE ? ", hash="+entity.hashCode() : "")) {{
                setOpaque(true);
                setIconTextGap(6);
                setVerticalAlignment(CENTER);

                ImageIcon icon = entity.getIcon();
                if (icon != null) {
                    int iconSize  = tree.getRowHeight()-2;
                    icon = ImageUtils.resize(icon, iconSize, iconSize);
                    setDisabledIcon(ImageUtils.grayscale(icon));
                    setIcon(icon);
                }

                boolean selected =
                        tree.getSelectionModel().getLeadSelectionPath() != null &&
                        tree.getSelectionModel().getLeadSelectionPath().getLastPathComponent() == value;

                setForeground(selected ? Color.WHITE : IEditor.COLOR_NORMAL);
                setBackground(selected ?
                        UIManager.getDefaults().getColor("Tree.selectionBackground") :
                        UIManager.getDefaults().getColor("Tree.background")
                );
                setBorder(new EmptyBorder(15, 2, 15, 7));
                setEnabled((entity.getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED);
            }};
        } else {
            return new DefaultTreeCellRenderer().getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }
    }
}
