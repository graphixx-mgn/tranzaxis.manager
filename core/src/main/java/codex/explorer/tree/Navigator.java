package codex.explorer.tree;

import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.IModelListener;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.LinkedList;
import java.util.List;

/**
 * Дерево навигации проводника.
 */
public final class Navigator extends JTree implements IModelListener, INodeListener {
    
    private TreePath path;
    private String   unitName;
    private final List<INavigateListener> listeners = new LinkedList<>();

    /**
     * Конструктор дерева.
     */
    public Navigator() {
        this(null);
    }

    public Navigator(Class unitClass) {
        super();
        unitName = unitClass == null ? "<?>" : Language.get(unitClass, "unit.title", Language.DEF_LOCALE);

        setRowHeight(IEditor.FONT_VALUE.getSize()*2-2);
        setBorder(new EmptyBorder(5, 10, 5, 5));
        addTreeSelectionListener((TreeSelectionEvent event) -> SwingUtilities.invokeLater(() -> {
            final INode node = (INode) getLastSelectedPathComponent();
            if (node == null) return;

            if ((node.getMode() & INode.MODE_SELECTABLE) != INode.MODE_SELECTABLE) {
                clearSelection();
                getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                return;
            }
            if (event.getOldLeadSelectionPath() != null) {
                Entity previous = (Entity) event.getOldLeadSelectionPath().getLastPathComponent();

                if (previous.getParent() != null && !previous.close()) {
                    clearSelection();
                    getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                    return;
                }
            }
            if (path != getSelectionModel().getSelectionPath()) {
                path = getSelectionModel().getSelectionPath();
                Logger.getLogger().debug("Selected path: [{0}]{1}", unitName, node.getPathString());
                new LinkedList<>(listeners).forEach((listener) -> SwingUtilities.invokeLater(() -> listener.nodeChanged(path)));
            }
        }));
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new GeneralRenderer());
    }
    
    /**
     * Добавление слушателя события смены активного узла дерева.
     * @param listener Слушатель.
     */
    public void addNavigateListener(INavigateListener listener) {
        listeners.add(listener);
    }

    @Override
    public void setModel(TreeModel model) {
        super.setModel(model);
        if (model instanceof NodeTreeModel) {
            model.addTreeModelListener(new TreeModelHandler() {
                @Override
                public void treeNodesInserted(TreeModelEvent e) {
                    if (e.getTreePath().getLastPathComponent() == model.getRoot())
                        expandPath(e.getTreePath());
                }

                @Override
                public void treeNodesRemoved(TreeModelEvent e) {
                    super.treeNodesRemoved(e);
                    for (Object node : e.getChildren()) {
                        if (path != null && path.getLastPathComponent() != null && path.getLastPathComponent().equals(node)) {
                            TreePath parentPath = path.getParentPath();
                            while (
                                    !parentPath.getLastPathComponent().equals(getModel().getRoot()) &&
                                    ((INode) parentPath.getLastPathComponent()).getParent() == null
                            ) {
                                parentPath = parentPath.getParentPath();
                            }
                            getSelectionModel().setSelectionPath(parentPath);
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void setExpandedState(TreePath path, boolean state) {
        if ((((INode) path.getLastPathComponent()).getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED) {
            super.setExpandedState(path, state);
        }
        if (path.getPathCount() == 1) {
            super.setExpandedState(path, true);
        }
    }
    
}
