package codex.explorer.tree;

import codex.log.Logger;
import java.awt.Font;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public final class Navigator extends JTree {
    
    private TreePath path;
    private final List<NavigateListener> listeners = new LinkedList<>();

    public Navigator() {
        super();
        
        setFont(new Font("Tahoma", 0, 12));
        setRowHeight(22);
        setBorder(new EmptyBorder(5, 10, 5, 2));
        addTreeSelectionListener((TreeSelectionEvent event) -> {
            final JTree tree = (JTree) event.getSource();
            final AbstractNode  node = (AbstractNode) tree.getLastSelectedPathComponent();
            if (node == null) return;
            
            if ((node.mode & INode.MODE_SELECTABLE) != INode.MODE_SELECTABLE) {
                tree.clearSelection();
                tree.getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                return;
            }
            if (path != tree.getSelectionModel().getSelectionPath()) {
                path = tree.getSelectionModel().getSelectionPath();
                Logger.getLogger().debug(
                        "Selected path: {0}",
                        String.join("/", Arrays.stream(tree.getSelectionPath().getPath()).map(Object::toString).toArray(String[]::new))
                );
                new LinkedList<>(listeners).stream().forEach((listener) -> {
                    listener.nodeChanged(path);
                });
            }
        });
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new NodeRenderer());
        ToolTipManager.sharedInstance().registerComponent(this);
    }
    
    public void addNavigateListener(NavigateListener listener) {
        listeners.add(listener);
    }
    
}
