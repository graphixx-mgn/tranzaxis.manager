package plugin;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.IInstanceListener;
import codex.instance.Instance;
import codex.instance.InstanceCommunicationService;
import codex.log.Logger;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.LocaleContextHolder;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TreeModelEvent;
import java.awt.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.*;
import java.util.List;
import java.util.stream.StreamSupport;

final class ShowPackagesUpdates extends EntityCommand<PluginCatalog> implements IInstanceListener, IPluginLoaderService.IPublicationListener {

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
    private final NodeTreeModel treeModel = new NodeTreeModel(new RemotePackageView(null, null) {
        @Override
        public Class<? extends Entity> getChildClass() {
            return RemotePackageView.class;
        }
    });

    public ShowPackagesUpdates() {
        super(
                "update",
                Language.get("title"),
                CMD_ICON,
                Language.get("title"),
                null
        );
        ICS.addInstanceListener(this);
        try {
            final PluginLoaderService ownPluginLoader = (PluginLoaderService) ICS.getService(PluginLoaderService.class);
            ownPluginLoader.addPublicationListener(this);
        } catch (NotBoundException e) {
            //
        }

        activator = pluginCatalogs -> {
            if (pluginCatalogs.isEmpty()) {
                return new CommandStatus(false);
            } else {
                synchronized (remotePackages) {
                    Map<String, IPluginLoaderService.RemotePackage> updateMap = new HashMap<>();
                    remotePackages.forEach(remotePackage -> {
                        PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
                        if (localPackage == null || VER_COMPARATOR.compare(remotePackage.getVersion(), localPackage.getVersion()) > 0) {
                            if (!updateMap.containsKey(remotePackage.getId())) {
                                updateMap.put(remotePackage.getId(), remotePackage);
                            } else if (VER_COMPARATOR.compare(remotePackage.getVersion(), updateMap.get(remotePackage.getId()).getVersion()) > 0) {
                                updateMap.put(remotePackage.getId(), remotePackage);
                            }
                        }
                    });
                    Collection<IPluginLoaderService.RemotePackage> updates = updateMap.values();

                    for (INode node: treeModel) {
                        RemotePackageView pkgView = (RemotePackageView) node;
                        if (pkgView != treeModel.getRoot()) {
                            if (!updates.contains(pkgView.remotePackage)) {
                                ((INode) treeModel.getRoot()).delete(pkgView);
                            } else {
                                pkgView.refreshUpgradeInfo();
                            }
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
                    treeModel.nodeStructureChanged((INode) treeModel.getRoot());

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
    public final void execute(PluginCatalog context, Map<String, IComplexType> params) {
        Dialog dialog = new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                CMD_ICON,
                Language.get(ShowPackagesUpdates.class, "title"),
                createView(),
                e -> {},
                Dialog.Default.BTN_CLOSE.newInstance()
        );
        treeModel.addTreeModelListener(new TreeModelAdapter() {
            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                if (((INode) treeModel.getRoot()).childrenList().isEmpty()) {
                    dialog.setVisible(false);
                }
            }
        });

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
    public final void instanceLinked(Instance instance) {
        SwingUtilities.invokeLater(() -> {
            try {
                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                registerPackages(instance, pluginLoader.getPublishedPackages(LocaleContextHolder.getLocale()));
            } catch (NotBoundException e) {
                //
            } catch (ClassCastException e) {
                //TODO: Временная заглушка до перехода на 2.2.2
                //Теперь передается наименования класса редактора, не кастектс в класс (а класс не мог быть сериализован)
            } catch (RemoteException e) {
                Logger.getLogger().warn("Failed remote service ''{0}'' call to instance ''{1}''", PluginLoaderService.class, instance);
            }
        });
    }

    @Override
    public final void instanceUnlinked(Instance instance) {
        SwingUtilities.invokeLater(() -> {
            unregisterPackages(instance, null);
        });
    }

    @Override
    public void publicationEvent(IPluginLoaderService.RemotePackage remotePackage, boolean published) {
        try {
            String remoteIP = RemoteServer.getClientHost();
            ICS.getInstances().forEach(instance -> {
                if (instance.getRemoteAddress().getAddress().getHostAddress().equals(remoteIP)) {
                    if (published) {
                        registerPackages(instance, Collections.singletonList(remotePackage));
                    } else {
                        unregisterPackages(instance, Collections.singletonList(remotePackage));
                    }
                }
            });
        } catch (ServerNotActiveException e) {
            e.printStackTrace();
        }
    }

    private void registerPackages(Instance instance, List<IPluginLoaderService.RemotePackage> packages) {
        synchronized (remotePackages) {
            packages.forEach(remotePackage -> {
                if (!remotePackages.contains(remotePackage)) {
                    remotePackages.add(remotePackage);
                }
                remotePackages.get(remotePackages.indexOf(remotePackage)).addInstance(instance);
            });
            activate();
        }
    }

    private void unregisterPackages(Instance instance, List<IPluginLoaderService.RemotePackage> packages) {
        synchronized (remotePackages) {
            remotePackages.removeIf(remotePackage -> {
                if (packages == null || packages.contains(remotePackage)) {
                    remotePackage.removeInstance(instance);
                    return !remotePackage.isAvailable();
                } else  {
                    return false;
                }
            });
            activate();
        }
    }
}
