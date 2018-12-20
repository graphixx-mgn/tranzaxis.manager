package codex.explorer.tree;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.swing.tree.DefaultTreeModel;

/**
 * Модель дерева проводника.
 */
public final class NodeTreeModel extends DefaultTreeModel implements Iterable<INode> {

    /**
     * Конструктор модели дерева.
     * @param root Корневой узел дерева.
     */
    public NodeTreeModel(INode root) {
        super(root);
    }

    @Override
    public Iterator<INode> iterator() {
        return new NodeIterator((INode) root);
    }
    
    private class NodeIterator implements Iterator<INode> {
        
        private final List<INode> list = new LinkedList<>();
        private final Iterator<INode> iterator;
        
        NodeIterator(INode node) {
            addChildren(node);
            iterator = list.iterator();
        }
        
        private void addChildren(INode parent) {
            list.add(parent);
            parent.childrenList().forEach((child) -> {
                if (child.isLeaf()) {
                    list.add(child);
                } else {
                    addChildren(child);
                }
            });
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public INode next() {
            return iterator.next();
        }
    
    }

}
