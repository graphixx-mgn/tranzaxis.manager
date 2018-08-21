package codex.explorer;

import codex.explorer.tree.NodeTreeModel;
import codex.model.Entity;
import java.util.List;
import java.util.stream.Collectors;
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
    public Entity getRoot() {
        return (Entity) model.getRoot();
    }

    @Override
    public List<Entity> getEntitiesByClass(Class entityClass) {
        return getRoot()
                .flattened()
                .filter((node) -> {
                    return entityClass.isAssignableFrom(node.getClass());
                })
                .map((node) -> {
                    return (Entity) node;
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public Entity getEntity(Class entityClass, Integer ID) {
        return getRoot()
                .flattened()
                .filter((node) -> {
                    return entityClass.isAssignableFrom(node.getClass()) && ((Entity) node).model.getID() == ID;
                })
                .map((node) -> {
                    return (Entity) node;
                })
                .findFirst().get();
    }
    
}
