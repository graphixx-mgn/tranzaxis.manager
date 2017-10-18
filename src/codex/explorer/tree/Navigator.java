package codex.explorer.tree;

import codex.log.Logger;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
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
    private final List<NavigateListener> listeners = new LinkedList<>();

    /**
     * Конструктор дерева.
     */
    public Navigator() {
        super();
        setRowHeight(22);
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
            if (path != tree.getSelectionModel().getSelectionPath()) {
                path = tree.getSelectionModel().getSelectionPath();
                Logger.getLogger().debug(
                        "Selected path: {0}",
                        "/" + String.join("/", node
                                .getPath()
                                .stream()
                                .skip(1)
                                .collect(Collectors.toList())
                        )
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
    
    /**
     * Добавление слушателя события смены активного узла дерева.
     */
    public void addNavigateListener(NavigateListener listener) {
        listeners.add(listener);
    }
    
}
