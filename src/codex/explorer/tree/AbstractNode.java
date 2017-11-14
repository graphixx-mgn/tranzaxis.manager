package codex.explorer.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Абстрактный узел дерева проводника.
 */
public abstract class AbstractNode implements INode {
    
    private INode parent = null; 
    private int   mode   = MODE_ENABLED + MODE_SELECTABLE;
    private final List<INode>         children = new ArrayList<>();
    private final List<INodeListener> nodeListeners = new LinkedList<>();
    
    /**
     * Возвращает перечисление потомков узла.
     */
    @Override
    public final Enumeration children() {
        return Collections.enumeration(new ArrayList<>(children));
    }
    
    /**
     * Возвращает родительский узел.
     */
    @Override
    public final INode getParent() {
        return parent;
    }

    @Override
    public final int getMode() {
        return mode;
    };
    
    @Override
    public final void setMode(int mode) {
        this.mode = mode;
    };
    
    @Override
    public final List<String> getPath() {
        List<String> path = getParent() != null ? getParent().getPath() : new LinkedList<>();
        path.add(toString());
        return path;
    }
    
    @Override
    public final String getPathString() {
        return "/" + String.join("/", this
                .getPath()
                .stream()
                .skip(1)
                .collect(Collectors.toList())
        );
    }
    
    @Override
    public void setParent(INode parent) {
        this.parent = parent;
    }
    
    @Override
    public final void addNodeListener(INodeListener listener) {
        nodeListeners.add(listener);
    };
    
    @Override
    public void insert(INode child) {
        child.setParent(this);
        children.add(child);
        new LinkedList<>(nodeListeners).forEach((listener) -> {
            listener.childInserted(this, child);
        });
    }

    @Override
    public void delete(INode child) {
        new LinkedList<>(nodeListeners).forEach((listener) -> {
            listener.childDeleted(this, child);
        });
        children.remove(child);
    };

}
