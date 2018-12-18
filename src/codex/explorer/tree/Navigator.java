package codex.explorer.tree;

import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.TreeNodeBalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.ToolTipUtils;

/**
 * Дерево навигации проводника.
 */
public final class Navigator extends JTree implements IModelListener, INodeListener {
    
    private TreePath path;
    private final List<INavigateListener> listeners = new LinkedList<>();

    /**
     * Конструктор дерева.
     */
    public Navigator() {
        super();
        setRowHeight(IEditor.FONT_VALUE.getSize()*2-2);
        setBorder(new EmptyBorder(5, 10, 5, 2));
        addTreeSelectionListener((TreeSelectionEvent event) -> {
            SwingUtilities.invokeLater(() -> {
                final INode node = (INode) getLastSelectedPathComponent();
                if (node == null) return;

                if ((node.getMode() & INode.MODE_SELECTABLE) != INode.MODE_SELECTABLE) {
                    clearSelection();
                    getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                    return;
                }
                if (event.getOldLeadSelectionPath() != null) {
                    Entity previous = (Entity) event.getOldLeadSelectionPath().getLastPathComponent();
                    if (!previous.close()) {
                        clearSelection();
                        getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                        return;
                    }
                }
                if (path != getSelectionModel().getSelectionPath()) {
                    path = getSelectionModel().getSelectionPath();
                    Logger.getLogger().debug("Selected path: {0}", node.getPathString());
                    new LinkedList<>(listeners).stream().forEach((listener) -> {
                        SwingUtilities.invokeLater(() -> {
                            listener.nodeChanged(path);
                        });
                    });
                    Entity current = (Entity) path.getLastPathComponent();
                }
            });
        });
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
            final Iterator<INode> it = ((NodeTreeModel) model).iterator();
            INode node;
            while (it.hasNext()) {
                node = it.next();
                setToolTip(new TreePath(((DefaultTreeModel) model).getPathToRoot(node)), node);
                node.addNodeListener(this);
                ((Entity) node).model.addModelListener(this);
            }
        }
    }
    
    private void setToolTip(TreePath path, INode node) {
        if (((Entity) node).getHint() == null) {
            return;
        }
        TreeNodeBalloonTip tip = new TreeNodeBalloonTip(
                this, 
                new JLabel(
                        ((Entity) node).getHint(),
                        ImageUtils.resize(
                            ImageUtils.getByPath("/images/event.png"), 
                            16, 16
                        ), SwingConstants.LEADING
                ),
                path, 
                new EdgedBalloonStyle(Color.WHITE, Color.GRAY), 
                BalloonTip.Orientation.LEFT_BELOW, 
                BalloonTip.AttachLocation.ALIGNED, 
                10, 10, false
        );
        ToolTipUtils.balloonToToolTip(tip, 1500, 3000);
    }

    @Override
    public void modelRestored(EntityModel model, List<String> changes) {
        Optional<INode> update = ((INode) (getModel()).getRoot()).flattened().filter((node) -> {
           return ((Entity) node).model == model;
        }).findFirst();
        if (update.isPresent()) {
            ((DefaultTreeModel) getModel()).nodeChanged(update.get());
        }
    }

    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        Optional<INode> update = ((INode) (getModel()).getRoot()).flattened().filter((node) -> {
           return ((Entity) node).model == model;
        }).findFirst();
        if (update.isPresent()) {
            ((DefaultTreeModel) getModel()).nodeChanged(update.get());
        }
    }
    
    @Override
    public void childInserted(INode parentNode, INode childNode) {
        childNode.addNodeListener(this);
        ((Entity) childNode).model.addModelListener(this);
        ((DefaultTreeModel) getModel()).nodesWereInserted(
                parentNode, 
                new int[] {parentNode.getIndex(childNode)}
        );
    }

    @Override
    public void childDeleted(INode parentNode, INode childNode, int index) {
        childNode.removeNodeListener(this);
        ((Entity) childNode).model.removeModelListener(this);
        ((DefaultTreeModel) getModel()).nodesWereRemoved(
                parentNode, 
                new int[] {index}, 
                new Object[] {childNode}
        );
    }
    
    @Override
    public void childMoved(INode parentNode, INode childNode) {
        ((DefaultTreeModel) getModel()).nodeStructureChanged(parentNode);
    }

    @Override
    public void childChanged(INode node) {
        ((DefaultTreeModel) getModel()).nodeChanged(node);
    }

    @Override
    protected void setExpandedState(TreePath path, boolean state) {
        if ((((INode) path.getLastPathComponent()).getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED) {
            super.setExpandedState(path, state);
        }
    }
    
}
