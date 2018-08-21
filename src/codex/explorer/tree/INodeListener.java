package codex.explorer.tree;

/**
 * Интерфейс слушателя событий изменения списка дочерних узлов.
 */
public interface INodeListener {
    
    /**
     * Добавлен новый дочерний узел.
     * @param parentNode Родительский узел.
     * @param childNode  Добавленный узел.
     */
    default void childInserted(INode parentNode, INode childNode) {};
    
    /**
     * Дочерний узел удален.
     * @param parentNode Родительский узел.
     * @param childNode  Добавленный узел.
     */
    default void childDeleted(INode parentNode, INode childNode, int index)  {};
    
    /**
     * Дочерний узел перемещен.
     * @param parentNode Родительский узел.
     * @param childNode  Добавленный узел.
     */
    default void childMoved(INode parentNode, INode childNode)  {};
    
    /**
     * Дочерний узел изменен.
     * @param node  узел.
     */
    default void childChanged(INode node)  {};
}
