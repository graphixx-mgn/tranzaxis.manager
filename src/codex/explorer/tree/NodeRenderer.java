package codex.explorer.tree;

import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

public class NodeRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Node node = (Node) value;
        Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        
        int iconSize = tree.getRowHeight()-2;
        ImageIcon icon = ImageUtils.resize(node.icon, iconSize, iconSize);
        setDisabledIcon(ImageUtils.grayscale(icon));
        setIcon(icon);
        
        setTextSelectionColor(Color.WHITE);
        setBackgroundSelectionColor(Color.decode("#3399FF"));
        setBorderSelectionColor(Color.GRAY);
        setToolTipText(node.hint);
        
        setEnabled((node.mode & NodeTreeModel.MODE_ENABLED) == NodeTreeModel.MODE_ENABLED);
        return component;
    }
    
}
