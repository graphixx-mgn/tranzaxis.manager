package plugin;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.IInstanceListener;
import codex.instance.Instance;
import codex.instance.InstanceCommunicationService;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.LocaleContextHolder;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class UpdatePackages extends EntityCommand<PluginCatalog> implements IInstanceListener {

    private final static InstanceCommunicationService ICS = (InstanceCommunicationService) ServiceRegistry.getInstance().lookupService(InstanceCommunicationService.class);
    private final static ImageIcon CMD_ICON = ImageUtils.getByPath("/images/update.png");

    private static final Comparator<String> VER_COMPARATOR = (ver1, ver2) -> {
        String[] vals1 = ver1.split("\\.");
        String[] vals2 = ver2.split("\\.");

        int i = 0;
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        } else {
            return Integer.signum(vals1.length - vals2.length);
        }
    };

    private final List<IPluginLoaderService.RemotePackage> remotePackages = new LinkedList<>();
    private final NodeTreeModel treeModel = new NodeTreeModel(new RemotePackageView(null, null));

    public UpdatePackages() {
        super(
                "update",
                Language.get("title"),
                CMD_ICON,
                Language.get("title"),
                null
        );
        ICS.addInstanceListener(this);

        activator = pluginCatalogs -> {
            if (pluginCatalogs.isEmpty()) {
                return new CommandStatus(false);
            } else {
                List<PluginPackage> localPackages = PluginManager.getInstance().getPluginLoader().getPackages();
                synchronized (remotePackages) {
                    List<IPluginLoaderService.RemotePackage> updates = remotePackages.stream()
                            .filter(remotePackage -> {
                                return
                                        localPackages.stream().noneMatch(localPackage -> localPackage.getId().equals(remotePackage.getId())) ||
                                        localPackages.stream().anyMatch(localPackage -> {
                                            return
                                                    localPackage.getId().equals(remotePackage.getId()) &&
                                                    VER_COMPARATOR.compare(remotePackage.getVersion(), localPackage.getVersion()) > 0;
                                        });
                            })
                            .collect(Collectors.toList());
                    for (INode node: treeModel) {
                        RemotePackageView pkgView = (RemotePackageView) node;
                        if (pkgView != treeModel.getRoot() && !updates.contains(pkgView.remotePackage)) {
                            ((INode) treeModel.getRoot()).delete(pkgView);
                        }
                    }
                    updates.forEach(remotePackage -> {
                        boolean nodeNotExists = StreamSupport.stream(treeModel.spliterator(), false).noneMatch(node ->
                                treeModel.getRoot() != node &&
                                ((RemotePackageView) node).remotePackage.equals(remotePackage)
                        );
                        if (nodeNotExists) {
                            ((INode) treeModel.getRoot()).insert(new RemotePackageView(remotePackage));
                        }
                    });
                    return new CommandStatus(
                            updates.size() > 0,
                            updates.size() == 0 ? CMD_ICON : ImageUtils.combine(
                                        CMD_ICON,
                                        ImageUtils.createBadge(String.valueOf(updates.size()), Color.decode("#DE5347"), Color.WHITE),
                                        SwingConstants.SOUTH_EAST
                            )
                    );
                }
            }
        };
    }

    @Override
    public Kind getKind() {
        return Kind.System;
    }

    @Override
    public void execute(PluginCatalog context, Map<String, IComplexType> params) {
        Dialog dialog = new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                CMD_ICON,
                Language.get(UpdatePackages.class, "title"),
                createView(),
                e -> {},
                Dialog.Default.BTN_CLOSE.newInstance()
        );
        dialog.setPreferredSize(new Dimension(800, 600));
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private JPanel createView() {
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(((Entity) treeModel.getRoot()).getSelectorPresentation());
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void instanceLinked(Instance instance) {
        SwingUtilities.invokeLater(() -> {
            try {
                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                pluginLoader.getPublishedPackages(LocaleContextHolder.getLocale()).forEach(remotePackage -> {
                    synchronized (remotePackages) {
                        if (!remotePackages.contains(remotePackage)) {
                            remotePackages.add(remotePackage);
                        }
                        remotePackages.get(remotePackages.indexOf(remotePackage)).addInstance(instance);
                        activate();
                    }
                });
            } catch (NotBoundException e) {
                //
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void instanceUnlinked(Instance instance) {
        SwingUtilities.invokeLater(() -> {
            synchronized (remotePackages) {
                remotePackages.removeIf(remotePackage -> {
                    remotePackage.removeInstance(instance);
                    return !remotePackage.isAvailable();
                });
                activate();
            }
        });
    }
}
