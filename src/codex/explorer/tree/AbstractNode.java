package codex.explorer.tree;

import codex.model.EntityModel;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.tree.TreeNode;

public abstract class AbstractNode implements INode {
    
    final ImageIcon icon;
    final String    title;
    final String    hint;
    
    int mode = MODE_ENABLED + MODE_SELECTABLE;
    
    private AbstractNode         parent = null; 
    private EditorPresentation   editor;
    private SelectorPresentation selector;
    
    public  final EntityModel        model;
    private final List<AbstractNode> children = new ArrayList<>();
    
    public AbstractNode(ImageIcon icon, String title, String hint) {
        this.title = title;
        this.icon  = icon;
        this.hint  = hint;
        this.model = new EntityModel(title);
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
    
    final void setParent(AbstractNode parent) {
        this.parent = parent;
    }
    
    public void insert(AbstractNode child) {
        child.setParent(this);
        children.add(child);
    }
    
    @Override
    public String toString() {
        return title;
    }

    @Override
    public Class getChildClass() {
        return null;
    };

    @Override
    public SelectorPresentation getSelectorPresentation() {
        if (getChildClass() == null) return null;
        if (selector == null) {
            selector = new SelectorPresentation();
        }
        return selector;
    };

    @Override
    public EditorPresentation getEditorPresentation() {
        if (editor == null) {
            editor = new EditorPresentation(this);
        }
        return editor;
    };
    
}
