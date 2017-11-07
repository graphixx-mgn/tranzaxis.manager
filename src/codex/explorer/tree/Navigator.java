package codex.explorer.tree;

import codex.log.Logger;
import codex.model.Entity;
import codex.model.IModelListener;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
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
public final class Navigator extends JTree implements IModelListener, TreeModelListener {
    
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
                previous.model.removeModelListener(this);
            }
            if (path != getSelectionModel().getSelectionPath()) {
                path = getSelectionModel().getSelectionPath();
                Logger.getLogger().debug("Selected path: {0}", node.getPathString());
                new LinkedList<>(listeners).stream().forEach((listener) -> {
                    listener.nodeChanged(path);
                });
                Entity current = (Entity) path.getLastPathComponent();
                current.model.addModelListener(this);
            }
        });
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new NodeRenderer());
    }
    
    /**
     * Добавление слушателя события смены активного узла дерева.
     */
    public void addNavigateListener(INavigateListener listener) {
        listeners.add(listener);
    }

    @Override
    public void setModel(TreeModel model) {
        if (getModel() != null) {
            getModel().removeTreeModelListener(this);
        }
        super.setModel(model);
        if (model instanceof NodeTreeModel) {
            for (int rowIdx = 0; rowIdx < getRowCount(); rowIdx++) {
                setToolTip(getPathForRow(rowIdx));
            }
        }
        model.addTreeModelListener(this);
    }
    
    private void setToolTip(TreePath path) {
        TreeNodeBalloonTip tip = new TreeNodeBalloonTip(
                    this, 
                    new JLabel(
                            ((Entity) path.getLastPathComponent()).getHint(), 
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
    public void modelRestored(List<String> changes) {
        treeNodesChanged(new TreeModelEvent(this, path));
    }

    @Override
    public void modelSaved(List<String> changes) {
        treeNodesChanged(new TreeModelEvent(this, path));
    }

    @Override
    public void treeNodesChanged(TreeModelEvent e) {
        repaint();
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
