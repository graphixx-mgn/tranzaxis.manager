package codex.explorer.tree;

import codex.log.Logger;
import codex.model.Entity;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Дерево навигации проводника.
 */
public final class Navigator extends JTree {
    
    private TreePath path;
    private final List<INavigateListener> listeners = new LinkedList<>();

    /**
     * Конструктор дерева.
     */
    public Navigator() {
        super();
        setRowHeight((int) (getRowHeight()*1.5));
        setBorder(new EmptyBorder(5, 10, 5, 2));
        addTreeSelectionListener((TreeSelectionEvent event) -> {
            final JTree tree = (JTree) event.getSource();
            final INode node = (INode) tree.getLastSelectedPathComponent();
            if (node == null) return;
            
            if ((node.getMode() & INode.MODE_SELECTABLE) != INode.MODE_SELECTABLE) {
                tree.clearSelection();
                tree.getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                return;
            }
            if (event.getOldLeadSelectionPath() != null) {
                if (!((Entity) event.getOldLeadSelectionPath().getLastPathComponent()).model.close()) {
                    tree.clearSelection();
                    tree.getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                    return;
                }
            }
            if (path != tree.getSelectionModel().getSelectionPath()) {
                path = tree.getSelectionModel().getSelectionPath();
                Logger.getLogger().debug("Selected path: {0}", node.getPathString());
                new LinkedList<>(listeners).stream().forEach((listener) -> {
                    listener.nodeChanged(path);
                });
            }
        });
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new NodeRenderer());
        ToolTipManager.sharedInstance().registerComponent(this);
    }
    
    /**
     * Добавление слушателя события смены активного узла дерева.
     */
    public void addNavigateListener(INavigateListener listener) {
        listeners.add(listener);
    }
    
}
