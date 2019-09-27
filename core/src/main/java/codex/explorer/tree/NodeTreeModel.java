package codex.explorer.tree;

import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import javax.swing.tree.DefaultTreeModel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Модель дерева проводника.
 */
public final class NodeTreeModel extends DefaultTreeModel implements Iterable<INode>, INodeListener, IModelListener {

    /**
     * Конструктор модели дерева.
     * @param root Корневой узел дерева.
     */
    public NodeTreeModel(INode root) {
        super(root);
        root.addNodeListener(this);
        ((Entity) root).model.addModelListener(this);
    }

    @Override
    public void childInserted(INode parentNode, INode childNode) {
        nodesWereInserted(
                parentNode,
                new int[] {parentNode.getIndex(childNode)}
        );
        childNode.addNodeListener(this);
        ((Entity) childNode).model.addModelListener(this);
    }

    @Override
    public void childDeleted(INode parentNode, INode childNode, int index) {
        childNode.removeNodeListener(this);
        ((Entity) childNode).model.removeModelListener(this);
        nodesWereRemoved(
                parentNode,
                new int[] {index},
                new Object[] {childNode}
        );
    }

    @Override
    public void childMoved(INode parentNode, INode childNode) {
        nodeStructureChanged(parentNode);
    }

    @Override
    public void childChanged(INode node) {
        nodeChanged(node);
    }

    @Override
    public void modelRestored(EntityModel model, List<String> changes) {
        ((INode) getRoot()).flattened()
                .filter((node) -> ((Entity) node).model == model)
                .findFirst()
                .ifPresent(this::nodeChanged);
    }

    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        ((INode) getRoot()).flattened()
                .filter((node) -> ((Entity) node).model == model)
                .findFirst()
                .ifPresent(this::nodeChanged);
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
