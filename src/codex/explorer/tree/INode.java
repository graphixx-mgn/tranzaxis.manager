package codex.explorer.tree;

import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.TreeNode;

/**
 * Интерфейс узла дерева проводника.
 */
public interface INode extends TreeNode {
    
    public static final int MODE_NONE       = 0;
    public static final int MODE_ENABLED    = 1;
    public static final int MODE_SELECTABLE = 2;
    
    /**
     * Возвращает режим отображения узла. 
     */
    int getMode();
    
    /**
     * Устанавливает режим отображения узла. 
     */
    void setMode(int mode);
    
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
     */
    void setParent(INode parent);
    
    /**
     * Вставить дочерний узел.
     */
    void insert(INode child);
    
    void move(INode child, int position);
    
    /**
     * Удалить дочерний узел.
     */
    void delete(INode child);
    
    /**
     * Возвращает список имен узлов от корневого до текущего.
     */
    List<String> getPath();
    
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
    default Class getChildClass() {
        return null;
    };
    
    void addNodeListener(INodeListener listener);

}
