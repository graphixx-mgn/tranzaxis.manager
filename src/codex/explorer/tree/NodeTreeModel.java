package codex.explorer.tree;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;


public final class NodeTreeModel implements TreeModel {
    
    public static final int MODE_NONE       = 0;
    public static final int MODE_ENABLED    = 1;
    public static final int MODE_SELECTABLE = 2;
    
    private final TreeNode root;
    private final List<TreeModelListener> listeners = new LinkedList<>();

    public NodeTreeModel(TreeNode root) {
        this.root = root;
    }
    
    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((TreeNode) parent).getChildAt(index);
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((TreeNode) parent).getChildCount();
    }

    @Override
    public boolean isLeaf(Object node) {
        if (node == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((TreeNode) node).isLeaf();
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((TreeNode) parent).getIndex((TreeNode) child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void valueForPathChanged(TreePath tp, Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public void setMode(Node node, int mode) {
        node.setMode(mode);
        for (TreeModelListener listener : new ArrayList<>(listeners)) {
            listener.treeNodesChanged(new TreeModelEvent(node, new Object[]{node}));
        }
    }
    
}
