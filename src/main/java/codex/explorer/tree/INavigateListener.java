package codex.explorer.tree;

import javax.swing.tree.TreePath;

/**
 * Интерфейс слушателя события изменения активного узла дерева проводника.
 * @see Navigator
 */
@FunctionalInterface
public interface INavigateListener  {

    /**
     * Вызывается при выделении узла дерева.
     * @param path Путь до выбранного узла.
     */
    public void nodeChanged(TreePath path);
    
}
