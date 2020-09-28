package plugin;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.MapEditor;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.*;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.apache.commons.codec.digest.DigestUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

final class PluginCatalog extends Catalog {

    private final static String PROP_ON_UPDATE    = "update.policy";
    private final static String PROP_UPDATE_RULES = "notify.rules";

    static {
        CommandRegistry.getInstance().registerCommand(UnitSettings.class);
        CommandRegistry.getInstance().registerCommand(ShowPackagesUpdates.class);
    }

    PluginCatalog() {
        this(null, null);
    }

    private PluginCatalog(EntityRef owner, String title) {
        super(
                null,
                ImageUtils.getByPath("/images/plugins.png"),
                title,
                null
        );

        // Properties
        model.addUserProp(PROP_ON_UPDATE, new Enum<>(OnUpdate.Notify), true, Access.Extra);
        model.addUserProp(
                PROP_UPDATE_RULES,
                new codex.type.Map<>(
                        new PackageId(),
                        new Enum<>(NotifyCondition.Never),
                        new LinkedHashMap<>()
                ),
                false, Access.Extra
        );

    }

    @Override
    public void attach(INode child) {
        super.attach(child);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return PackageView.class;
    }

    @Override
    public void loadChildren() {}

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }

    OnUpdate onUpdateOption() {
        return (OnUpdate) model.getValue(PROP_ON_UPDATE);
    }

    PackageView getView(PluginPackage pluginPackage) {
        return childrenList().stream()
                .map(iNode -> (PackageView) iNode)
                .filter(packageView -> packageView.getPackage().equals(pluginPackage))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    final void setRestriction(PackageDescriptor descriptor) throws Exception {
        Map.Entry<PackageDescriptor, NotifyCondition> restriction = findRestriction(descriptor.getPackageId());

        if (restriction == null) {
            NotifyCondition condition = createRestriction(NotifyCondition.OnConsistChanges, descriptor);
            Map<PackageDescriptor, NotifyCondition> oldRules = (Map<PackageDescriptor, NotifyCondition>) (model.getUnsavedValue(PROP_UPDATE_RULES));
            Map<PackageDescriptor, NotifyCondition> newRules = new LinkedHashMap<>(oldRules);
            newRules.put(descriptor, condition);
            model.setValue(PROP_UPDATE_RULES, newRules);
            model.commit(true, PROP_UPDATE_RULES);
        } else {
            String oldVersion = restriction.getKey().getVersion();
            String newVersion = descriptor.getVersion();
            if (PluginPackage.VER_COMPARATOR.compare(newVersion, oldVersion) > 0) {
                NotifyCondition condition = createRestriction(restriction.getValue(), descriptor);
                Map<PackageDescriptor, NotifyCondition> oldRules = (Map<PackageDescriptor, NotifyCondition>) (model.getUnsavedValue(PROP_UPDATE_RULES));
                Map<PackageDescriptor, NotifyCondition> newRules = new LinkedHashMap<>();
                oldRules.forEach((storedDescriptor, storedRule) -> {
                    if (storedDescriptor.getPackageId().equals(descriptor.getPackageId())) {
                        newRules.put(descriptor, condition);
                    } else {
                        newRules.put(storedDescriptor, storedRule);
                    }
                });
                model.setValue(PROP_UPDATE_RULES, newRules);
                model.commit(true, PROP_UPDATE_RULES);
            }
        }
    }

    final boolean checkRestriction(IPluginLoaderService.RemotePackage remotePackage) {
        Map.Entry<PackageDescriptor, NotifyCondition> restriction = findRestriction(remotePackage.getTitle());
        return restriction == null || restriction.getValue().check(restriction.getKey(), remotePackage);
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<PackageDescriptor, NotifyCondition> findRestriction(String packageId) {
        return ((Map<PackageDescriptor, NotifyCondition>) (model.getUnsavedValue(PROP_UPDATE_RULES))).entrySet().stream()
                .filter(rule -> rule.getKey().getPackageId().equals(packageId))
                .findFirst().orElse(null);
    }

    private NotifyCondition createRestriction(NotifyCondition defaultCondition, PackageDescriptor descriptor) {
        final String propName = "condition";

        PropertyHolder<Enum<NotifyCondition>, NotifyCondition> propHolder = new PropertyHolder<>(
                propName, new Enum<>(defaultCondition), false
        );
        ParamModel model = new ParamModel();
        model.addProperty(propHolder);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(model.getEditor(propName).getEditor());
        container.add(Box.createRigidArea(new Dimension(1, 28)));
        container.setBorder(new EmptyBorder(0,10,0,10));

        JLabel propLabel = new JLabel(
                MessageFormat.format(
                        Language.get("create.rule@message"),
                        descriptor.getPackageId()
                ),
                ImageUtils.getByPath("/images/confirm.png"),
                SwingConstants.LEFT
        );
        propLabel.setIconTextGap(10);
        propLabel.setBorder(new EmptyBorder(10,10,10,10));

        Dialog dialog = new Dialog(
                Dialog.findNearestWindow(),
                ImageUtils.getByPath("/images/plus.png"),
                Language.get("create.rule@title"),
                new JPanel(new BorderLayout()) {{
                    add(propLabel, BorderLayout.NORTH);
                    add(container, BorderLayout.CENTER);
                }},
                e -> {},
                Dialog.Default.BTN_OK
        );
        dialog.setResizable(false);
        dialog.setVisible(true);
        return propHolder.getPropValue().getValue();
    }


    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        PackageView   packageView = (PackageView) entity;
        PluginPackage pluginPackage = packageView.getPackage();
        try {
            if (packageView.isPublished()) {
                packageView.getCommand(PackageView.PublishPackage.class).execute(packageView, Collections.emptyMap());
            }
            PluginManager.getInstance().getPluginLoader().removePluginPackage(pluginPackage, true, true);
            for (PluginHandler pluginHandler : pluginPackage.getPlugins()) {
                Entity.deleteInstance(pluginHandler.getView(), false, false);
            }
            Entity.deleteInstance(entity, false, false);
        } catch (PluginException | IOException e) {
            Logger.getLogger().warn(MessageFormat.format("Unable to remove plugin package ''{0}''", pluginPackage), e);
            if (e instanceof PluginException && ((PluginException) e).isHandled()) return;
            MessageBox.show(
                    MessageType.WARNING,
                    MessageFormat.format(
                            Language.get(PackageView.class, "delete@error"),
                            e.getMessage()
                    )
            );
        }
    }


    enum OnUpdate implements Iconified {

        Install(ImageUtils.getByPath("/images/plugin_install.png")),
        Notify(ImageUtils.getByPath("/images/notify.png"));

        private final ImageIcon icon;
        private final String title;

        OnUpdate(ImageIcon icon) {
            this.icon  = icon;
            this.title = Language.get(PluginCatalog.class, "update@".concat(name().toLowerCase()));
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }


        @Override
        public String toString() {
            return title;
        }
    }


    enum NotifyCondition implements Iconified {
        Never(
                ImageUtils.getByPath("/images/unavailable.png"),
                (idHolder, remotePackage) -> false
        ),
        OnConsistChanges(
                ShowPackagesUpdates.ICON_NEW,
                (descriptor, remotePackage) -> {
                    return remotePackage.getPlugins().stream()
                            .map(IPluginLoaderService.RemotePlugin::getPluginId)
                            .map(DigestUtils::md5Hex)
                            .anyMatch(remotePluginId -> !descriptor.getPlugins().contains(remotePluginId));
                }
        ),
        OnNextVersion(
                ShowPackagesUpdates.ICON_UPD,
                (descriptor, remotePackage) -> PluginPackage.VER_COMPARATOR.compare(remotePackage.getVersion(), descriptor.getVersion()) > 0
        );

        private final ImageIcon icon;
        private final String    title = Language.get(PluginCatalog.class, "condition@"+name().toLowerCase());
        private final BiPredicate<PackageDescriptor, IPluginLoaderService.RemotePackage> condition;

        NotifyCondition(ImageIcon icon, BiPredicate<PackageDescriptor, IPluginLoaderService.RemotePackage> condition) {
            this.icon = icon;
            this.condition = condition;
        }

        boolean check(PackageDescriptor idHolder, IPluginLoaderService.RemotePackage remotePackage) {
            Logger.getContextLogger(PluginLoader.class).debug(
                    "Check notification restrictions for ''{0}'' [type={1}, stored={2}, remote={3}]",
                    remotePackage, name(), idHolder.getVersion(), remotePackage.getVersion()
            );
            return condition.test(idHolder, remotePackage);
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }
    }


    private static class PackageId extends ArrStr {

        private PackageDescriptor value = null;

        private PackageId() {}

        @Override
        public void setValue(List<String> value) {
            if (value != null) {
                this.value = new PackageDescriptor(value);
            } else {
                this.value = null;
            }
        }

        @Override
        public List<String> getValue() {
            return value;
        }

        @Override
        public boolean isEmpty() {
            return value == null || value.isEmpty();
        }

        @Override
        public String toString() {
            return merge(value);
        }

        @Override
        public String getQualifiedValue(List<String> val) {
            return val == null ? "<NULL>" :  MessageFormat.format("({0}-{1})'", val.get(0), val.get(1));
        }
    }


    static class PackageDescriptor extends LinkedList<String> implements Iconified {

        private PackageDescriptor() {
            super();
        }

        private PackageDescriptor(Collection<? extends String> c) {
            super(c);
        }

        PackageDescriptor(String name, String version, List<String> plugins) {
            super(Arrays.asList(name, version));
            addAll(plugins);
        }

        private String getPackageId() {
            return get(0);
        }

        private String getVersion() {
            return get(1);
        }

        private List<String> getPlugins() {
            return subList(2, size());
        }

        @Override
        public String toString() {
            return MessageFormat.format("{0} [{1}]", get(0), get(1));
        }

        @Override
        public ImageIcon getIcon() {
            return PackageView.PACKAGE;
        }
    }


    static class UnitSettings extends EntityCommand<PluginCatalog> {

        private static final String    COMMAND_TITLE = Language.get(PluginCatalog.class, "unit@settings");
        private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
                ImageUtils.getByPath("/images/update.png"),
                ImageUtils.resize(ImageUtils.getByPath("/images/general.png"), .68f),
                SwingConstants.SOUTH_EAST
        );

        public UnitSettings() {
            super("unit settings", COMMAND_TITLE, COMMAND_ICON, COMMAND_TITLE, null);
        }

        @Override
        public void execute(PluginCatalog context, Map<String, IComplexType> params) {
            DialogButton btnSubmit = Dialog.Default.BTN_OK.newInstance();
            DialogButton btnCancel = Dialog.Default.BTN_CANCEL.newInstance();

            List<String> extraPropNames = context.model.getProperties(Access.Any).stream()
                    .filter(context.model::isPropertyExtra)
                    .collect(Collectors.toList());

            ParamModel paramModel = new ParamModel();
            extraPropNames.stream()
                    .map(context.model::getProperty)
                    .forEach(propertyHolder -> {
                        paramModel.addProperty(propertyHolder);
                        paramModel.addPropertyGroup(context.model.getPropertyGroup(propertyHolder.getName()), propertyHolder.getName());
                    });

            paramModel.addChangeListener((name, oldValue, newValue) -> {
                List<String> changes = context.model.getChanges();
                btnSubmit.setEnabled(!changes.isEmpty());
                paramModel.getEditor(name).getLabel().setText(paramModel.getProperty(name).getTitle() + (changes.contains(name) ? " *" : ""));
            });

            btnSubmit.setEnabled(context.model.getChanges().stream().anyMatch(extraPropNames::contains));
            ((MapEditor) paramModel.getEditor(PROP_UPDATE_RULES)).setMode(EnumSet.of(MapEditor.EditMode.DeleteAllowed));

            final codex.component.dialog.Dialog editor = new codex.component.dialog.Dialog(
                    Dialog.findNearestWindow(),
                    COMMAND_ICON,
                    COMMAND_TITLE,
                    new JPanel(new BorderLayout()) {{
                        add(new EditorPage(paramModel), BorderLayout.NORTH);
                    }},
                    (event) -> {
                        if (event.getID() == codex.component.dialog.Dialog.OK) {
                            if (context.model.hasChanges()) {
                                try {
                                    context.model.commit(true);
                                } catch (Exception e) {
                                    context.model.rollback();
                                }
                            }
                        } else {
                            if (context.model.hasChanges()) {
                                context.model.rollback();
                            }
                        }
                    },
                    btnSubmit, btnCancel
            ) {{
                // Перекрытие обработчика кнопок
                Function<DialogButton, ActionListener> defaultHandler = handler;
                handler = (button) -> (event) -> {
                    if (event.getID() != Dialog.OK || context.getInvalidProperties().isEmpty()) {
                        defaultHandler.apply(button).actionPerformed(event);
                    }
                };
            }
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(700, super.getPreferredSize().height);
                }
            };

            context.model.getProperties(Access.Edit).stream()
                    .map(context.model::getEditor)
                    .forEach((propEditor) -> propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentHidden(ComponentEvent e) {
                            editor.pack();
                        }

                        @Override
                        public void componentShown(ComponentEvent e) {
                            editor.pack();
                        }
                    }));

            editor.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            editor.setResizable(false);
            editor.setVisible(true);
        }

        @Override
        public Kind getKind() {
            return Kind.System;
        }
    }
}
