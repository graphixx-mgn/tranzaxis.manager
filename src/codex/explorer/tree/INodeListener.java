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
    default void childDeleted(INode parentNode, INode childNode)  {};
    
}
