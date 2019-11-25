package codex.explorer.tree;

import codex.model.Entity;
import codex.presentation.EditorPage;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.tree.TreeNode;

/**
 * Интерфейс узла дерева проводника.
 */
public interface INode extends TreeNode {
    
    /**
     * Узел не может быть выбран и не активен.
     */
    public static final int MODE_NONE       = 0;
    /**
     * Узел активен.
     */
    public static final int MODE_ENABLED    = 1;
    /**
     * Узел может быть выбран.
     */
    public static final int MODE_SELECTABLE = 2;
    
    /**
     * Возвращает режим отображения узла. 
     */
    int getMode();
    
    /**
     * Устанавливает режим отображения узла. 
     * @param mode Режим отображения узла 
     * (см. {@link INode#MODE_NONE}, {@link INode#MODE_ENABLED}, {@link INode#MODE_SELECTABLE})
     */
    void setMode(int mode);

    /**
     * Возвраящает страницу редактора модели.
     */
    EditorPage getEditorPage();
    
    /**
     * Возвращает презентацию редактора узла. 
     */
    EditorPresentation getEditorPresentation();
    
    /**
     * Возвращает презентацию селектора узла. 
     */
    SelectorPresentation getSelectorPresentation();
    
    /**
     * Установить родительский узел.
     * @param parent Ссылка на родительский узел.
     */
    void setParent(INode parent);
    
    /**
     * Вставить дочерний узел.
     * @param child Ссылка на дочерний узел.
     */
    void attach(INode child);
    
    /**
     * Перемещение дочернего узла.
     * @param child Ссылка на дочерний узел.
     * @param position Индекс новой позиции.
     */
    void move(INode child, int position);
    
    /**
     * Удалить дочерний узел.
     * @param child Ссылка на дочерний узел.
     */
    void detach(INode child);
    
    /**
     * Возвращает список имен узлов от корневого до текущего.
     */
    List<INode> getPath();
    
    /**
     * Возвращает путь до текущего узла.
     */
    String getPathString();
    
    /**
     * Возвращает признак что узел не имеет потомков.
     */
    @Override
    default boolean isLeaf() {
        return getChildCount() == 0;
    }
    
    /**
     * Возвращает признак что узлу разрешено иметь потомков.
     */
    @Override
    default boolean getAllowsChildren() {
        return true;
    }
    
    /**
     * Возвращает количество потомков узла.
     */
    @Override
    default int getChildCount() {
        return childrenList().size();
    }
    
    /**
     * Возвращает список всех потомков узла.
     */
    default List<INode> childrenList() {
        return Collections.list(children());
    }
    
    /**
     * Возвращает потомка узла по его индексу.
     * @param childIndex Индекс дочернего узла.
     */
    @Override
    default INode getChildAt(int childIndex) {
        if (childrenList().isEmpty()) {
            throw new ArrayIndexOutOfBoundsException("Node has no children");
        }
        if (childrenList().size() <= childIndex || childIndex < 0) {
            throw new ArrayIndexOutOfBoundsException("Index out range");
        }
        return childrenList().get(childIndex);
    }
    
    /**
     * Возвращает индекс потомка узла.
     * @param node Ссылк на дочерний узед.
     */
    @Override
    default int getIndex(TreeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        if (node == this) {
            return -1;
        }
        return childrenList().indexOf(node);
    }
    
    /**
     * Возвращает класс потомков узла.
     */
    default Class<? extends Entity> getChildClass() {
        return null;
    }
    
    /**
     * Разрешено ли редактирование списка дочерних сущностей (
     *  * добавление
     *  * удаление
     *  * изменение порядка
     * ).
     */
    default boolean allowModifyChild() {
        return true;
    };
    
    /**
     * Получение списка всех дочерних элементов рекурсивно. В начале списка 
     * стоит этот узел.
     */
    public Stream<INode> flattened();
    
    /**
     * Добавить слушатель событий узла {@link INodeListener}.
     * @param listener Слушатель.
     */
    void addNodeListener(INodeListener listener);
    /**
     * Удалить слушатель событий узла {@link INodeListener}.
     * @param listener Слушатель.
     */
    void removeNodeListener(INodeListener listener);
    
}
