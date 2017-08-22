package codex.explorer.tree;

import javax.swing.tree.TreeNode;

public interface Node extends TreeNode {
    
    public static final int MODE_NONE       = 0;
    public static final int MODE_ENABLED    = 1;
    public static final int MODE_SELECTABLE = 2;
    
}
