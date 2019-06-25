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
    public List<? extends Entity> getEntitiesByClass(Class<? extends Entity> entityClass) {
        return getRoot()
                .flattened()
                .filter((node) -> entityClass.isAssignableFrom(node.getClass()))
                .map((node) -> (Entity) node)
                .collect(Collectors.toList());
    }
    
    @Override
    public Entity getEntity(Class<? extends Entity> entityClass, Integer ID) {
        return getRoot()
                .flattened()
                .filter((node) -> entityClass.isAssignableFrom(node.getClass()) && ((Entity) node).getID() == ID)
                .map((node) -> (Entity) node)
                .findFirst().orElse(null);
    }
    
}
