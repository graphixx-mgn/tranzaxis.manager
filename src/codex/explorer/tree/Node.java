package codex.explorer.tree;

import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import javax.swing.tree.TreeNode;

public interface Node extends TreeNode {
    
    public static final int MODE_NONE       = 0;
    public static final int MODE_ENABLED    = 1;
    public static final int MODE_SELECTABLE = 2;
    
    public EditorPresentation getEditorPresentation();
    public SelectorPresentation getSelectorPresentation();
    public Class getChildClass();
    
}
