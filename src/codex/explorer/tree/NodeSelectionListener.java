package codex.explorer.tree;

import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

public class NodeSelectionListener implements TreeSelectionListener {

    @Override
    public void valueChanged(TreeSelectionEvent event) {
        final JTree tree = (JTree) event.getSource();
        final Node  node = (Node) tree.getLastSelectedPathComponent();
        if (node == null) return;
        if ((node.mode & Node.MODE_SELECTABLE) != Node.MODE_SELECTABLE) {
            tree.clearSelection();
            tree.getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
            return;
        }
        
    }
    
}
