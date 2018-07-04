package codex.explorer;

import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.Entity;
import java.util.LinkedList;
import java.util.List;
import javax.swing.tree.TreeModel;

/**
 * Реализация интерфейса сервиса доступа проводнику.
 */
public class ExplorerAccessService implements IExplorerAccessService {
    
    private final NodeTreeModel model;
    
    /**
     * Конструктор сервиса.
     * @param model Модель дерева проводника.
     */
    ExplorerAccessService(TreeModel model) {
        this.model = (NodeTreeModel) model;
    } 

    @Override
    public List<Entity> getEntitiesByClass(Class entityClass) {
        List<Entity> found = new LinkedList<>();
        for (INode node : model) {
            if (entityClass.isAssignableFrom(node.getClass())) {
                found.add((Entity) node);
            }
        }
        return found;
    }
    
    @Override
    public Entity getEntity(Class entityClass, Integer ID) {
        for (INode node : model) {
            if (entityClass.equals(node.getChildClass())) {
                return (Entity) node.childrenList().stream().filter((child) -> {
                    return ((Entity) child).model.getID() == ID;
                }).findFirst().orElse(null);
            }
            if (entityClass.isAssignableFrom(node.getClass()) && ((Entity) node).model.getID().equals(ID)) {
                return (Entity) node;
            }
        }
        return null;
    }
    
}
