package codex.instance;

import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.INode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;

/**
 * Модуль отображения подключенных инстанций.
 */
public class InstanceUnit extends AbstractUnit {

    private static final InstanceUnit INSTANCE = new InstanceUnit();
    
    public static final InstanceUnit getInstance() {
        return INSTANCE;
    }
    
    private ExplorerUnit explorer;
    private final NodeTreeModel instancesTree;
    
    private InstanceUnit() {
        instancesTree = new NodeTreeModel(new Localhost());
        try {
            Constructor ctor = ExplorerUnit.class.getDeclaredConstructor(BrowseMode.class);
            ctor.setAccessible(true);
            explorer = (ExplorerUnit) ctor.newInstance(new EmbeddedMode());
            explorer.createViewport();

            Field navigatorField = ExplorerUnit.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);

            Navigator navigator  = (Navigator) navigatorField.get(explorer);
            navigator.setModel(instancesTree);
        } catch (Exception e) {
            //
        }

        ServiceRegistry.getInstance().addRegistryListener(IInstanceDispatcher.class, service -> {
            IInstanceDispatcher dispatcher = (IInstanceDispatcher) service;
            dispatcher.getInstances().forEach((instance) -> {
                INode root = (INode) instancesTree.getRoot();
                root.insert(new RemoteHost(instance));
                if (root.getChildCount() > 0) {
                    explorer.getViewport();
                    explorer.viewportBound();
                }
            });

            dispatcher.addInstanceListener(new IInstanceListener() {
                @Override
                public void instanceLinked(Instance instance) {
                    INode root = (INode) instancesTree.getRoot();
                    root.insert(new RemoteHost(instance));
                    if (root.getChildCount() > 0) {
                        explorer.getViewport();
                        explorer.viewportBound();
                    }
                }

                @Override
                public void instanceUnlinked(Instance instance) {
                    INode root = (INode) instancesTree.getRoot();
                    getViews().stream()
                            .filter((view) -> view.getInstance().equals(instance))
                            .forEach(root::delete);
                }
            });
        });
    }

    @Override
    public JComponent createViewport() {
        return explorer.getViewport();
    }

    @Override
    public void viewportBound() {
        explorer.viewportBound();
    }
    
    private List<RemoteHost> getViews() {
        return ((INode) instancesTree.getRoot()).childrenList().stream()
                .map((node) -> (RemoteHost) node)
                .collect(Collectors.toList());
    }
    
}
