package plugin;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.IInstanceDispatcher;
import codex.instance.IInstanceListener;
import codex.instance.Instance;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.notification.Handler;
import codex.notification.INotificationService;
import codex.notification.Message;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.LocaleContextHolder;
import manager.xml.Change;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

final class ShowPackagesUpdates extends EntityCommand<PluginCatalog>
        implements
            IInstanceListener,
            IPluginLoaderService.IPublicationListener,
            PluginLoader.ILoaderListener
{

    private final static ImageIcon CMD_ICON = ImageUtils.getByPath("/images/update.png");
    private final static ImageIcon ICON_NEW = ImageUtils.combine(
            ImageUtils.getByPath("/images/repository.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 20, 20),
            SwingConstants.SOUTH_EAST
    );
    private final static ImageIcon ICON_UPD = ImageUtils.combine(
            ImageUtils.getByPath("/images/repository.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/up.png"), 20, 20),
            SwingConstants.SOUTH_EAST
    );

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
            refreshUpdates();
            int  totalUpdates  = updateTreeModel.getRoot().childrenList().size();
            long activeUpdates = updateTreeModel.getRoot().childrenList().stream()
                    .filter(iNode -> ((RemotePackageView) iNode).remotePackage.isAvailable())
                    .count();
            return new CommandStatus(
                    totalUpdates > 0,
                    activeUpdates == 0 ? CMD_ICON : ImageUtils.combine(
                                CMD_ICON,
                                ImageUtils.createBadge(String.valueOf(activeUpdates), Color.decode("#3399FF"), Color.WHITE),
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
        dialog.setPreferredSize(new Dimension(800, 600));
        dialog.setResizable(false);
        dialog.setVisible(true);
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

    @Override
    public void packageLoaded(PluginPackage pluginPackage) {
        Optional<IPluginLoaderService.RemotePackage> remote = remotePackages.stream()
                .filter(remotePackage -> remotePackage.getId().equals(pluginPackage.getId()))
                .findFirst();
        if (remote.isPresent()) {
            for (Instance instance : remote.get().getInstances()) {
                remote.get().removeInstance(instance);
            }
            Optional<RemotePackageView> existPkgView = updateTreeModel.getRoot().childrenList().stream()
                    .map(iNode -> (RemotePackageView) iNode)
                    .filter(pkgView -> {
                        PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(pluginPackage.getId());
                        return pkgView.remotePackage.getId().equals(localPackage.getId());
                    })
                    .findFirst();
            existPkgView.ifPresent(remotePackageView -> updateTreeModel.getRoot().detach(remotePackageView));
            activate();
        }
    }

    private synchronized void registerPackages(Instance instance, List<IPluginLoaderService.RemotePackage> packages) {
        synchronized (remotePackages) {
            boolean changed = false;
            for (IPluginLoaderService.RemotePackage remotePackage : packages) {
                remotePackage.addInstance(instance);

                boolean isNew = isNew(remotePackage);
                boolean isUpd = !isNew && isUpdate(remotePackage);
                if (isUpd && getCatalog().onUpdateOption() == PluginCatalog.OnUpdate.Install) {
                    isUpd = !DownloadPackages.installPackage(remotePackage);
                }
                if (isNew || isUpd) {
                    if (isNew) {
                        ServiceRegistry.getInstance().lookupService(INotificationService.class).sendMessage(newPackageMessage(remotePackage), Handler.Inbox);
                    }
                    if (isUpd) {
                        ServiceRegistry.getInstance().lookupService(INotificationService.class).sendMessage(updPackageMessage(remotePackage), Handler.Inbox);
                    }
                    if (!remotePackages.contains(remotePackage)) {
                        remotePackages.add(remotePackage);
                    }
                    changed = true;
                }
            }
            if (changed) activate();
        }
    }

    private void unregisterPackages(Instance instance, List<IPluginLoaderService.RemotePackage> packages) {
        synchronized (remotePackages) {
            for (IPluginLoaderService.RemotePackage remotePackage : remotePackages) {
                remotePackage.removeInstance(instance);
            }
            activate();
        }
    }

    private boolean isNew(IPluginLoaderService.RemotePackage remotePackage) {
        PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
        return localPackage == null;
    }

    private boolean isUpdate(IPluginLoaderService.RemotePackage remotePackage) {
        PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
        return localPackage != null && PluginPackage.VER_COMPARATOR.compare(remotePackage.getVersion(), localPackage.getVersion()) > 0;
    }

    private synchronized void refreshUpdates() {
        for (IPluginLoaderService.RemotePackage remotePackage : remotePackages) {
            Optional<RemotePackageView> existPkgView = updateTreeModel.getRoot().childrenList().stream()
                    .map(iNode -> (RemotePackageView) iNode)
                    .filter(pkgView -> pkgView.remotePackage.equals(remotePackage))
                    .findFirst();
            if (existPkgView.isPresent()) {
                boolean changed = PluginPackage.VER_COMPARATOR.compare(
                        remotePackage.getVersion(),
                        existPkgView.get().remotePackage.getVersion()
                ) != 0;
                if (changed) {
                    updateTreeModel.getRoot().replace(
                            new RemotePackageView(remotePackage),
                            updateTreeModel.getRoot().getIndex(existPkgView.get())
                    );
                    continue;
                }
                existPkgView.get().setMode(remotePackage.isAvailable() ? INode.MODE_ENABLED : INode.MODE_NONE);
            } else if (remotePackage.isAvailable()) {
                updateTreeModel.getRoot().attach(new RemotePackageView(remotePackage));
            }
        }
        updateTreeModel.nodeStructureChanged(updateTreeModel.getRoot());
    }

    private static String getPluginTitle(IPluginLoaderService.RemotePlugin remotePlugin) {
        return remotePlugin.getProperties().stream()
                .filter(property -> property.getName().equals(EntityModel.THIS))
                .map(IPluginLoaderService.PropertyPresentation::getValue)
                .findFirst().orElse(Language.NOT_FOUND);
    }

    private static String getPluginDescription(IPluginLoaderService.RemotePlugin remotePlugin) {
        return remotePlugin.getProperties().stream()
                .filter(property -> property.getName().equals(Plugin.PROP_DESC))
                .map(IPluginLoaderService.PropertyPresentation::getValue)
                .findFirst().orElse(Language.NOT_FOUND);
    }

    private Message newPackageMessage(IPluginLoaderService.RemotePackage remotePackage) {
        return Message.getBuilder(remotePackage::toString)
            .setSeverity(Message.Severity.Information)
            .setSubject(MessageFormat.format(
                    Language.get("msg@new.title"),
                    remotePackage.getTitle(),
                    remotePackage.getVersion()
            ))
            .setContent(MessageFormat.format(
                    Language.get("msg@new.template"),
                    ImageUtils.toBase64(ICON_NEW), 20,
                    remotePackage.getTitle(),
                    remotePackage.getVersion(),
                    remotePackage.getPlugins().stream()
                            .map(remotePlugin -> MessageFormat.format(
                                    Language.get("msg@new.row"),
                                    getPluginTitle(remotePlugin),
                                    getPluginDescription(remotePlugin)
                            ))
                            .collect(Collectors.joining())
            ))
            .build();
    }

    private Message updPackageMessage(IPluginLoaderService.RemotePackage remotePackage) {
        PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
        Map<Change.Type.Enum, List<Change>> changes = Arrays.stream(remotePackage.getChanges().getVersions().getVersionArray())
                .map(version -> Arrays.stream(version.getChangelog().getChangeArray()))
                .flatMap(x -> x)
                .collect(Collectors.groupingBy(Change::getType));
        List<IPluginLoaderService.RemotePlugin> newPlugins = remotePackage.getPlugins().stream()
                .filter(remotePlugin -> localPackage.getPlugins().stream()
                        .noneMatch(pluginHandler -> remotePlugin.getPluginId().equals(Plugin.getId(pluginHandler)))
                )
                .collect(Collectors.toList());

        return Message.getBuilder(remotePackage::toString)
                .setSeverity(changes.keySet().contains(Change.Type.BUGFIX) ? Message.Severity.Warning : Message.Severity.Information)
                .setSubject(MessageFormat.format(
                        Language.get("msg@upd.title"),
                        remotePackage.getTitle(),
                        remotePackage.getVersion()
                ))
                .setContent(MessageFormat.format(
                        Language.get("msg@upd.template"),
                        ImageUtils.toBase64(ICON_UPD), 20,
                        remotePackage.getTitle(),
                        localPackage.getVersion(),
                        remotePackage.getVersion(),
                        String.join("",
                                !changes.containsKey(Change.Type.BUGFIX) ? "" : MessageFormat.format(
                                        Language.get("msg@upd.row.bugfix"),
                                        changes.get(Change.Type.BUGFIX).stream()
                                                .map(change -> MessageFormat.format("<li>{0}</li>", change.getDescription()))
                                                .collect(Collectors.joining())
                                ),
                                !changes.containsKey(Change.Type.FEATURE) ? "" : MessageFormat.format(
                                        Language.get("msg@upd.row.whatsnew"),
                                        changes.get(Change.Type.FEATURE).stream()
                                                .filter(change -> change.getScope() != Change.Scope.API)
                                                .map(change -> MessageFormat.format(
                                                        "<li>{0}</li>",
                                                        change.getDescription()
                                                                .replaceAll("\\n", "<br>")
                                                                .replaceAll("\\*", "&nbsp;&bull;")
                                                ))
                                                .collect(Collectors.joining())
                                ),
                                newPlugins.isEmpty() ? "" : MessageFormat.format(
                                        Language.get("msg@upd.row.plugins"),
                                        newPlugins.stream()
                                                .map(remotePlugin -> MessageFormat.format(
                                                        "<li><b>{0}</b><br>{1}</li>",
                                                        getPluginTitle(remotePlugin),
                                                        getPluginDescription(remotePlugin)
                                                ))
                                                .collect(Collectors.joining())
                                )
                        )
                ))
                .build();
    }
}
