package codex.explorer.tree;

import java.util.LinkedList;
import java.util.List;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Модель дерева проводника.
 */
public final class NodeTreeModel implements TreeModel {
    
    private final INode root;
    private final List<TreeModelListener> listeners = new LinkedList<>();

    /**
     * Конструктор модели дерева.
     * @param root Корневой узел дерева.
     */
    public NodeTreeModel(INode root) {
        this.root = root;
    }
    
    /**
     * Возвращает корневой узел.
     */
    @Override
    public INode getRoot() {
        return root;
    }

    /**
     * Возвращает потомка указанного узла по его индексу.
     */
    @Override
    public INode getChild(Object parent, int index) {
        if (parent == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((INode) parent).getChildAt(index);
    }

    /**
     * Возвращает количество потомков указанного узла.
     */
    @Override
    public int getChildCount(Object parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((INode) parent).getChildCount();
    }

    /**
     * Возвращает признак что указанный узел не имеет потомков. 
     */
    @Override
    public boolean isLeaf(Object node) {
        if (node == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((INode) node).isLeaf();
    }

    /**
     * Возвращает индекс потомка узла.
     * @param parent Узел в котором производится поиск совпадения.
     * @param child Узел индекс которого требуется определить.
     * @return 
     */
    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return ((INode) parent).getIndex((INode) child);
    }

    /**
     * Добавить слушатель событий изменения моделт дерева.
     */
    @Override
    public void addTreeModelListener(TreeModelListener listener) {
        listeners.add(listener);
    }

    /**
     * Удалить слушателя событий изменения моделт дерева.
     */
    @Override
    public void removeTreeModelListener(TreeModelListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void valueForPathChanged(TreePath tp, Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
//    public void setMode(INode node, int mode) {
//        node.setMode(mode);
//        new ArrayList<>(listeners).stream().forEach((listener) -> {
//            listener.treeNodesChanged(new TreeModelEvent(node, new INode[]{node}));
//        });
//    }
    
}
