package codex.instance;

import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.INode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.GridBagLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;


/**
 * Модуль отображения подключенных инстанций.
 */
public class InstanceUnit extends AbstractUnit {
    
    private static final ImageIcon ICON_ERROR  = ImageUtils.getByPath("/images/warn.png");
    private static final InstanceUnit INSTANCE = new InstanceUnit();
    
    public static final InstanceUnit getInstance() {
        return INSTANCE;
    }
    
    private ExplorerUnit explorer;
    private final NodeTreeModel instancesTree;
    
    private InstanceUnit() {
        if (!ServiceRegistry.getInstance().isServiceRegistered(InstanceCommunicationService.class)) {
            explorer = null;
            instancesTree = null;
            return;
        }
        
        InstanceCommunicationService ICS = (InstanceCommunicationService) ServiceRegistry.getInstance().lookupService(InstanceCommunicationService.class);
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
        } catch (Exception e) {/**/}
        
        ICS.getInstances().forEach((instance) -> {
            INode root = (INode) instancesTree.getRoot();
            root.insert(new RemoteHost(instance));
            if (root.getChildCount() > 0) {
                explorer.getViewport();
                explorer.viewportBound();
            }
        });
        
        ICS.addInstanceListener(new IInstanceListener() {
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
    }

    @Override
    public JComponent createViewport() {
        if (ServiceRegistry.getInstance().isServiceRegistered(InstanceCommunicationService.class)) {
            return explorer.getViewport();
        } else {
            JPanel panel = new JPanel();
            panel.setLayout(new GridBagLayout());
            panel.add(new JLabel(
                    Language.get(InstanceUnit.class, "error@notstarted"),
                    ICON_ERROR, SwingConstants.CENTER));
            return panel;
        }
    }

    @Override
    public void viewportBound() {
        if (!ServiceRegistry.getInstance().isServiceRegistered(InstanceCommunicationService.class)) return;
        explorer.viewportBound();
    }
    
    private List<RemoteHost> getViews() {
        return ((INode) instancesTree.getRoot()).childrenList().stream()
                .map((node) -> (RemoteHost) node)
                .collect(Collectors.toList());
    }
    
}
