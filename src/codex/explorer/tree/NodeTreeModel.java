package codex.explorer.tree;

import javax.swing.tree.DefaultTreeModel;

/**
 * Модель дерева проводника.
 */
public final class NodeTreeModel extends DefaultTreeModel {

    /**
     * Конструктор модели дерева.
     * @param root Корневой узел дерева.
     */
    public NodeTreeModel(INode root) {
        super(root);
    }

}
