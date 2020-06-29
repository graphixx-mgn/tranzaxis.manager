package plugin;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.IInstanceDispatcher;
import codex.instance.IInstanceListener;
import codex.instance.Instance;
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
import javax.swing.event.TreeModelListener;
import java.awt.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

final class ShowPackagesUpdates extends EntityCommand<PluginCatalog>
        implements
            IInstanceListener,
            IPluginLoaderService.IPublicationListener,
            PluginLoader.ILoaderListener
{

    private final static ImageIcon CMD_ICON = ImageUtils.getByPath("/images/update.png");

    private final List<IPluginLoaderService.RemotePackage> remotePackages = new LinkedList<>();

    private final NodeTreeModel updateTreeModel = new NodeTreeModel(new RemotePackageView(null, null) {
        @Override
        public Class<? extends Entity> getChildClass() {
            return RemotePackageView.class;
        }
    });

    public ShowPackagesUpdates() {
        super(
                "update plugins",
                Language.get("title"),
                CMD_ICON,
                Language.get("title"),
                null
        );
        ServiceRegistry.getInstance().addRegistryListener(IInstanceDispatcher.class, service -> {
            IInstanceDispatcher localICS = (IInstanceDispatcher) service;
            localICS.addInstanceListener(this);
        });

        activator = pluginCatalogs -> {
            int updatesCount = refreshUpdates();
            return new CommandStatus(
                    updatesCount > 0,
                    updatesCount == 0 ? CMD_ICON : ImageUtils.combine(
                                CMD_ICON,
                                ImageUtils.createBadge(String.valueOf(updatesCount), Color.decode("#3399FF"), Color.WHITE),
                                SwingConstants.SOUTH_EAST
                    )
            );
        };
    }

    private PluginCatalog getCatalog() {
        return PluginManager.getInstance().getPluginCatalog();
    }

    @Override
    public Kind getKind() {
        return Kind.System;
    }

    @Override
    public final void execute(PluginCatalog context, Map<String, IComplexType> params) {
        Dialog dialog = new Dialog(
                Dialog.findNearestWindow(),
                CMD_ICON,
                Language.get(ShowPackagesUpdates.class, "title"),
                new JPanel(new BorderLayout()) {{
                    add(new JScrollPane(((Entity) updateTreeModel.getRoot()).getSelectorPresentation()) {{
                        getViewport().setBackground(Color.WHITE);
                        setBorder(new CompoundBorder(
                                new EmptyBorder(5, 5, 5, 5),
                                new MatteBorder(1, 1, 1, 1, Color.LIGHT_GRAY)
                        ));
                    }}, BorderLayout.CENTER);
                }},
                e -> {},
                codex.component.dialog.Dialog.Default.BTN_CLOSE.newInstance()
        );
        TreeModelListener listener = new TreeModelAdapter() {
            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                if (updateTreeModel.getRoot().getChildCount() == 0) {
                    dialog.setVisible(false);
                }
            }
        };
        updateTreeModel.addTreeModelListener(listener);

        dialog.setPreferredSize(new Dimension(800, 600));
        dialog.setResizable(false);
        dialog.setVisible(true);

        updateTreeModel.removeTreeModelListener(listener);
    }

    @Override
    public final void instanceLinked(Instance instance) {
        SwingUtilities.invokeLater(() -> {
            try {
                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                registerPackages(instance, pluginLoader.getPublishedPackages(LocaleContextHolder.getLocale()));
            } catch (NotBoundException e) {
                //
            } catch (RemoteException e) {
                Logger.getLogger().warn(MessageFormat.format("Failed remote service ''{0}'' call to instance ''{1}''", PluginLoaderService.class, instance), e);
            }
        });
    }

    @Override
    public final void instanceUnlinked(Instance instance) {
        SwingUtilities.invokeLater(() -> unregisterPackages(instance, null));
    }

    @Override
    public void publicationEvent(IPluginLoaderService.RemotePackage remotePackage, boolean published) {
        Instance remoteInstance = Instance.getRemoteInstance();
        if (remoteInstance != null) {
            if (published) {
                registerPackages(remoteInstance, Collections.singletonList(remotePackage));
            } else {
                unregisterPackages(remoteInstance, Collections.singletonList(remotePackage));
            }
        }
    }

    private synchronized void registerPackages(Instance instance, List<IPluginLoaderService.RemotePackage> packages) {
        synchronized (remotePackages) {
            for (IPluginLoaderService.RemotePackage remotePackage : packages) {
                if (!remotePackages.contains(remotePackage)) {
                    remotePackages.add(remotePackage);
                }
                remotePackages.get(remotePackages.indexOf(remotePackage)).addInstance(instance);
            }
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

    private synchronized int refreshUpdates() {
        final Map<String, IPluginLoaderService.RemotePackage> updateMap = new HashMap<>();
        for (IPluginLoaderService.RemotePackage remotePackage : remotePackages) {
            PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
            boolean isUpdated =
                    localPackage != null &&
                    PluginPackage.VER_COMPARATOR.compare(remotePackage.getVersion(), localPackage.getVersion()) > 0;
            boolean isInstalled = false;
            if (isUpdated && getCatalog().onUpdateOption() == PluginCatalog.OnUpdate.Install) {
                isInstalled = DownloadPackages.installPackage(remotePackage);
            }
            if (localPackage == null || (isUpdated && !isInstalled)) {
                if (!updateMap.containsKey(remotePackage.getId())) {
                    updateMap.put(remotePackage.getId(), remotePackage);
                } else if (PluginPackage.VER_COMPARATOR.compare(remotePackage.getVersion(), updateMap.get(remotePackage.getId()).getVersion()) > 0) {
                    updateMap.replace(remotePackage.getId(), remotePackage);
                }
            }
        }

        updateTreeModel.getRoot().childrenList().forEach(iNode -> {
            RemotePackageView pkgView = (RemotePackageView) iNode;
            if (updateMap.values().contains(pkgView.remotePackage)) {
                pkgView.refreshUpgradeInfo();
            } else {
                updateTreeModel.getRoot().detach(pkgView);
            }
        });
        updateMap.forEach((id, remotePackage) -> {
            if (updateTreeModel.getRoot().childrenList().stream().noneMatch(iNode -> ((RemotePackageView) iNode).remotePackage.equals(remotePackage))) {
                updateTreeModel.getRoot().attach(new RemotePackageView(remotePackage));
            }
        });
        updateTreeModel.nodeStructureChanged(updateTreeModel.getRoot());
        return updateTreeModel.getRoot().getChildCount();
    }

    @Override
    public void packageLoaded(PluginPackage pluginPackage) {
        activate();
    }
}
