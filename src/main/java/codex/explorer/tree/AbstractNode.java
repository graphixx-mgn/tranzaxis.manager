package codex.explorer.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Абстрактный узел дерева проводника.
 */
public abstract class AbstractNode implements INode {
    
    private INode parent = null; 
    private int   mode   = MODE_ENABLED + MODE_SELECTABLE;
    private final List<INode>         children = Collections.synchronizedList(new LinkedList<>());
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
    }
    
    @Override
    public final void setMode(int mode) {
        if (this.mode != mode) {
            this.mode = mode;
            fireChangeEvent();
        }
    }
    
    /**
     * Гнерирует событие изменения узла.
     */
    protected final void fireChangeEvent() {
        new LinkedList<>(nodeListeners).forEach((listener) -> {
            listener.childChanged(this);
        });
    }
    
    @Override
    public final List<INode> getPath() {
        List<INode> path = getParent() != null ? getParent().getPath() : new LinkedList<>();
        path.add(this);
        return path;
    }
    
    @Override
    public final String getPathString() {
        return "/".concat(
                this
                .getPath()
                .stream()
                .skip(1)
                .map(Object::toString)
                .collect(Collectors.joining("/"))
        );
    }
    
    @Override
    public void setParent(INode parent) {
        this.parent = parent;
    }
    
    @Override
    public final void addNodeListener(INodeListener listener) {
        synchronized (nodeListeners) {
            if (!nodeListeners.contains(listener)) {
                nodeListeners.add(listener);
            }
        }
    }
    
    @Override
    public final void removeNodeListener(INodeListener listener) {
        synchronized (nodeListeners) {
            nodeListeners.remove(listener);
        }
    }
    
    @Override
    public void insert(INode child) {
        child.setParent(this);
        children.add(child);
        new LinkedList<>(nodeListeners).forEach((listener) -> listener.childInserted(this, child));
    }
    
    @Override
    public void move(INode child, int position) {
        if (position >= 0 && position < children.size() && getIndex(child) != position) {
            children.remove(child);
            children.add(position, child);
            new LinkedList<>(nodeListeners).forEach((listener) -> listener.childMoved(this, child));
        }
    }

    @Override
    public void delete(INode child) {
        int index = children.indexOf(child);
        children.remove(child);
        child.setParent(null);
        new LinkedList<>(nodeListeners).forEach((listener) -> listener.childDeleted(this, child, index));
    }
    
    private Semaphore lock;
    /**
     * Возвращает объект блокировки сущности.
     */
    public final Semaphore getLock() {
        if (lock == null) {
            lock = new Semaphore(1, true) {
                @Override
                public void acquire() throws InterruptedException {
                    super.acquire();
                    fireChangeEvent();
                }
                
                @Override
                public void release() {
                    if (availablePermits() == 0) {
                        // Avoid extra releases that increases permits counter
                        super.release();
                    }
                    fireChangeEvent();
                }
            };
        }
        return lock;
    }
    
    /**
     * Возвращает признак блокировки сущности.
     */
    public final boolean islocked() {
        return getLock().availablePermits() == 0;
    }
    
    @Override
    public Stream<INode> flattened() {
        return Stream.concat(
                Stream.of(this),
                childrenList().stream().flatMap(INode::flattened)
        );
    }
    
    @Override
    public boolean isLeaf() {
        return getChildCount() == 0 || !allowModifyChild();
    }
    
}
