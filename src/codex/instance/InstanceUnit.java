package codex.instance;

import codex.explorer.ExplorerUnit;
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
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

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
            Constructor ctor = ExplorerUnit.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            explorer = (ExplorerUnit) ctor.newInstance();
        } catch (
            NoSuchMethodException     | 
            SecurityException         | 
            IllegalAccessException    | 
            InstantiationException    |
            InvocationTargetException |
            IllegalArgumentException  e) {}
        
        ICS.addInstanceListener(new IInstanceListener() {
            @Override
            public void instanceLinked(Instance instance) {
                INode root = (INode) instancesTree.getRoot();
                root.insert(new RemoteHost(instance));
                if (root.getChildCount() > 0) {
                    explorer.viewportBound();
                }
            }

            @Override
            public void instanceUnlinked(Instance instance) {
                INode root = (INode) instancesTree.getRoot();
                getViews().stream().filter((view) -> {
                    return view.getHost().equals(instance.host) && view.getUser().equals(instance.user);
                }).forEach((view) -> {
                    root.delete(view);
                });
            }
            
        });
    }

    @Override
    public JComponent createViewport() {
        if (ServiceRegistry.getInstance().isServiceRegistered(InstanceCommunicationService.class)) {
            return explorer.createViewport();
        } else {
            JPanel panel = new JPanel();
            panel.setLayout(new GridBagLayout());
            panel.add(new JLabel(
                    Language.get(InstanceUnit.class.getSimpleName(), "error@notstarted"),
                    ICON_ERROR, SwingConstants.CENTER));
            return panel;
        }
    }

    @Override
    public void viewportBound() {
        if (!ServiceRegistry.getInstance().isServiceRegistered(InstanceCommunicationService.class)) return;
        
        try {
            Field navigatorField = ExplorerUnit.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);
            
            Navigator navigator  = (Navigator) navigatorField.get(explorer);
            navigator.setModel(instancesTree);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {}
        explorer.viewportBound();
    }
    
    private List<RemoteHost> getViews() {
        return ((INode) instancesTree.getRoot()).childrenList().stream().map((node) -> {
            return (RemoteHost) node;
        }).collect(Collectors.toList());
    }
    
}
