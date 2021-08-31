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
    default void childInserted(INode parentNode, INode childNode) {}
    
    /**
     * Дочерний узел удален.
     * @param parentNode Родительский узел.
     * @param childNode  Удаленный узел.
     * @param index Индекс позиции удаленного узла в списке потомков.
     */
    default void childDeleted(INode parentNode, INode childNode, int index)  {}
    
    /**
     * Дочерний узел перемещен.
     * @param parentNode Родительский узел.
     * @param childNode  Добавленный узел.
     */
    default void childMoved(INode parentNode, INode childNode, int from, int to)  {}

    default void childReplaced(INode prevChild, INode nextChild) {}
    
    /**
     * Дочерний узел изменен.
     * @param node  узел.
     */
    default void childChanged(INode node)  {}
}
