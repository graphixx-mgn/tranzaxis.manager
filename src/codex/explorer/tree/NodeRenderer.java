package codex.explorer.tree;

import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Рендерер элементов дерева проводника.
 */
public class NodeRenderer extends DefaultTreeCellRenderer {
    
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
        Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        
        int iconSize = tree.getRowHeight()-2;
        ImageIcon icon;
        if (!entity.model.isValid()) {
            icon = ImageUtils.resize(ImageUtils.combine(
                entity.icon,
                ImageUtils.getByPath("/images/warn.png")    
            ), iconSize, iconSize);
        } else {
            icon = ImageUtils.resize(entity.icon, iconSize, iconSize);
        }
        setDisabledIcon(ImageUtils.grayscale(icon));
        setIcon(icon);
        
        setTextSelectionColor(Color.WHITE);
        setBackgroundSelectionColor(Color.decode("#3399FF"));
        setBorderSelectionColor(Color.GRAY);
        setBorder(new EmptyBorder(15, 0, 15, 0));
        setToolTipText(entity.hint);
        
        setEnabled((entity.getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED);
        return component;
    }
    
}
