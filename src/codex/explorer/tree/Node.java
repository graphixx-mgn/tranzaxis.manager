package codex.explorer.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.tree.TreeNode;

public final class Node implements TreeNode {
    
    public static final int MODE_NONE       = 0;
    public static final int MODE_ENABLED    = 1;
    public static final int MODE_SELECTABLE = 2;
    
    final String title;
    final ImageIcon icon;
    int mode = MODE_ENABLED + MODE_SELECTABLE;
    private Node parent = null;
    private final List<Node> children = new ArrayList<>();
    
    public Node(String title, ImageIcon icon) {
        this.title = title;
        this.icon  = icon;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        if (children.isEmpty()) {
            throw new ArrayIndexOutOfBoundsException("Node has no children");
        }
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }
    
    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode child) {
        if (child == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        if (child == this) {
            return -1;
        }
        return children.indexOf(child);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(new ArrayList<>(children));
    }
    
    void setParent(Node parent) {
        this.parent = parent;
    }
    
    public Node setMode(int mode) {
        this.mode = mode;
        return this;
    }
    
    public void insert(Node child) {
        child.setParent(this);
        children.add(child);
    }
    
    @Override
    public String toString() {
        return title;
    }
}
