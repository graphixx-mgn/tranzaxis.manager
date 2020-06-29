package plugin;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditorFactory;
import codex.editor.TextView;
import codex.log.Logger;
import codex.model.*;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Attributes;

public class Plugin<P extends IPlugin> extends Catalog {

    final static String PROP_TYPE    = "type";
    final static String PROP_DESC    = "desc";
    final static String PROP_ENABLED = "enabled";
    final static String PROP_OPTIONS = "options";
    final static List<String> VIEW_PROPS = Arrays.asList(PROP_TYPE, PROP_DESC);

    final static ImageIcon ICON_OPTIONS = ImageUtils.getByPath("/images/general.png");
    final static ImageIcon ICON_WARN = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), .7f);

    static {
        CommandRegistry.getInstance().registerCommand(
                Plugin.class,
                EditOptions.class,
                Plugin::hasOptions
        );
        CommandRegistry.getInstance().registerCommand(LoadPlugin.class);
        CommandRegistry.getInstance().registerCommand(UnloadPlugin.class);
    }

    static String getId(PluginHandler pluginHandler) {
        try {
            Attributes attributes = PluginPackage.getAttributes(new File(
                    ((URLClassLoader) pluginHandler.pluginClass.getClassLoader()).getURLs()[0].getFile()
            ));
            return MessageFormat.format(
                    "{0}.{1}/{2}",
                    attributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR),
                    attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE),
                    pluginHandler.pluginClass.getCanonicalName().toLowerCase()
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private final PluginHandler<P> pluginHandler;
    private final ParamModel       pluginOptions;

    public Plugin(PluginHandler<P> pluginHandler) {
        super(null, null, getId(pluginHandler), null);
        this.pluginHandler = pluginHandler;

        setIcon(pluginHandler.getIcon());
        setTitle(pluginHandler.toString());

        // Properties
        model.addDynamicProp(PROP_TYPE, new AnyType(), null, pluginHandler::getDescription);

        // Plugin type properties
        pluginHandler.getTypeDefinition().forEach((propName, valueSupplier) -> model.addDynamicProp(
                propName,
                Language.get(pluginHandler.getClass(), propName+ PropertyHolder.PROP_NAME_SUFFIX),
                Language.get(pluginHandler.getClass(), propName+PropertyHolder.PROP_DESC_SUFFIX),
                new AnyType(),
                Access.Select,
                valueSupplier
        ));

        // Plugin text description
        model.addDynamicProp(PROP_DESC, new AnyType() {
            @Override
            public IEditorFactory<AnyType, Object> editorFactory() {
                return TextView::new;
            }
        }, Access.Select, () -> Language.get(pluginHandler.pluginClass, "desc"));

        // Internal properties
        model.addUserProp(PROP_ENABLED, new Bool(false), false, Access.Any);
        model.addUserProp(PROP_OPTIONS, new codex.type.Map<>(new Str(), new Str(), new LinkedHashMap<>()), false, Access.Any);


        // Plugin options
        this.pluginOptions = getPluginOptions();

        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            if (PROP_ENABLED.equals(name)) {
                setMode(MODE_SELECTABLE + (newValue == Boolean.TRUE ? MODE_ENABLED : MODE_NONE));
            }
        });
        setMode(MODE_SELECTABLE + (isEnabled() ? MODE_ENABLED : MODE_NONE));
    }

    final void setEnabled(boolean value, boolean commit) {
        model.setValue(PROP_ENABLED, value);
        if (commit) {
            try {
                model.commit(false);
            } catch (Exception ignore) {}
        }
    }

    final boolean isEnabled() {
        return model.getUnsavedValue(PROP_ENABLED) == Boolean.TRUE;
    }

    final boolean loadAllowed() {
        return !isEnabled() && isOptionsValid();
    }

    final List<String> getProperties() {
        return new LinkedList<String>() {{
            add(PROP_TYPE);
            addAll(pluginHandler.getTypeDefinition().keySet());
            add(PROP_DESC);
        }};
    }

    Object getOption(String optName) {
        if (!pluginOptions.getParameters().containsKey(optName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Plugin does not have option ''{0}''", optName)
            );
        }
        return pluginOptions.getValue(optName);
    }

    boolean hasOptions() {
        return !getPluginOptions().getParameters().isEmpty();
    }

    boolean isOptionsValid() {
        return pluginOptions.isValid();
    }

    private ParamModel getPluginOptions() {
        if (pluginOptions != null) {
            return pluginOptions;
        }
        Pluggable.PluginOptions pluginOptions = pluginHandler.getPluginClass().getAnnotation(Pluggable.PluginOptions.class);
        if (pluginOptions != null) {
            Class<? extends Pluggable.OptionsProvider> providerClass = pluginOptions.provider();
            Pluggable.OptionsProvider provider = null;
            try {
                if (providerClass.isMemberClass() && !Modifier.isStatic(providerClass.getModifiers())) {
                    for (Constructor<?> ctor : providerClass.getDeclaredConstructors()) {
                        Class<?> paramClass = ctor.getParameterTypes()[0];
                        if (paramClass.equals(pluginHandler.getPluginClass())) {
                            ctor.setAccessible(true);
                            //noinspection unchecked
                            provider = ((Constructor<? extends Pluggable.OptionsProvider>) ctor).newInstance(pluginHandler.getPluginClass().cast(null));
                            break;
                        }
                    }
                } else {
                    Constructor<? extends Pluggable.OptionsProvider> ctor = providerClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    provider = ctor.newInstance();
                }
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException |InvocationTargetException e) {
                e.printStackTrace();
            }
            if (provider != null) {
                ParamModel paramModel = provider.getOptions();
                //noinspection unchecked
                Map<String, String> dbOptValues = (Map<String, String>) model.getValue(PROP_OPTIONS);
                dbOptValues.forEach((optName, optStrValue) -> {
                    if (paramModel.hasProperty(optName)) {
                        IComplexType optValue = paramModel.getParameters().get(optName);
                        optValue.valueOf(optStrValue);
                    }
                });
                return paramModel;
            }
        }
        return new ParamModel();
    }

    private void setPluginOptions(Map<String, String> options) throws Exception {
        model.setValue(PROP_OPTIONS, options);
        model.commit(true);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }


    static class LoadPlugin extends EntityCommand<Plugin> {

        LoadPlugin() {
            super(
                    "load plugin",
                    Language.get(Plugin.class, "load@title"),
                    ImageUtils.getByPath("/images/start.png"),
                    Language.get(Plugin.class, "load@title"),
                    Plugin::loadAllowed
            );
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

        @Override
        public void execute(Plugin context, Map<String, IComplexType> params) {
            try {
                context.pluginHandler.loadPlugin();
                context.setEnabled(true, true);
            } catch (PluginException e) {
                String pluginId = getId(context.pluginHandler);
                Logger.getLogger().warn(MessageFormat.format("Unable to load plugin ''{0}''", pluginId), e);
                if (e.isHandled()) return;
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(Plugin.class, "load@error"),
                                e.getMessage()
                        )
                );
            }
        }
    }


    static class UnloadPlugin extends EntityCommand<Plugin> {

        UnloadPlugin() {
            super(
                    "unload plugin",
                    Language.get(Plugin.class, "unload@title"),
                    ImageUtils.getByPath("/images/stop.png"),
                    Language.get(Plugin.class, "unload@title"),
                    Plugin::isEnabled
            );
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

        @Override
        public void execute(Plugin context, Map<String, IComplexType> params) {
            try {
                if (context.pluginHandler.unloadPlugin()) {
                    context.setEnabled(false, true);
                }
            } catch (PluginException e) {
                String pluginId = getId(context.pluginHandler);
                Logger.getLogger().warn(MessageFormat.format("Unable to unload plugin ''{0}''", pluginId), e);
                if (e.isHandled()) return;
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(Plugin.class, "unload@error"),
                                e.getMessage()
                        )
                );
            }
        }
    }


    static class EditOptions extends EntityCommand<Plugin> {

        private EditOptions() {
            super(
                    "edit options",
                    Language.get(Plugin.class, "options@title"),
                    ICON_OPTIONS,
                    Language.get(Plugin.class, "options@title"),
                    null
            );
            Function<List<Plugin>, CommandStatus> defaultActivator = activator;
            activator = entities -> {
                boolean hasInvalidProp = entities.stream()
                        .anyMatch(plugin -> !plugin.isOptionsValid());
                return new CommandStatus(
                        defaultActivator.apply(entities).isActive(),
                        hasInvalidProp ? ImageUtils.combine(getIcon(), ICON_WARN, SwingConstants.SOUTH_EAST) : getIcon()
                );
            };
        }

        @Override
        public boolean multiContextAllowed() {
            return false;
        }

        @Override
        public void execute(Plugin context, Map<String, IComplexType> params) {
            ParamModel paramModel = context.getPluginOptions();
            EditorPage editorPage = new EditorPage(paramModel);

            editorPage.setBorder(new CompoundBorder(
                    new EmptyBorder(10, 5, 5, 5),
                    new TitledBorder(new LineBorder(Color.LIGHT_GRAY, 1), context.toString())
            ));
            new Dialog(
                    Dialog.findNearestWindow(),
                    ICON_OPTIONS,
                    getTitle(),
                    editorPage,
                    event -> {
                        if (event.getID() == Dialog.OK) {
                            try {
                                Map<String, String> dbOptValues = new LinkedHashMap<>();
                                paramModel.getParameters().forEach((optName, optValue) -> {
                                    if (optValue instanceof ISerializableType) {
                                        dbOptValues.put(optName, optValue.toString());
                                    }
                                });
                                context.setPluginOptions(dbOptValues);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            //noinspection unchecked
                            Map<String, String> dbOptValues = (Map<String, String>) context.model.getValue(PROP_OPTIONS);
                            dbOptValues.forEach((optName, optStrValue) -> {
                                if (paramModel.hasProperty(optName)) {
                                    IComplexType optValue = paramModel.getParameters().get(optName);
                                    optValue.valueOf(optStrValue);
                                }
                            });
                        }
                    },
                    Dialog.Default.BTN_OK,
                    Dialog.Default.BTN_CANCEL
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, ActionListener> defaultHandler = handler;
                    handler = (button) -> (event) -> {
                        if (event.getID() != Dialog.OK || paramModel.isValid() || !context.isEnabled()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    };
                }
                @Override
                public Dimension getPreferredSize() {
                    Dimension prefSize = super.getPreferredSize();
                    return new Dimension(650, prefSize.getSize().height);
                }
            }.setVisible(true);
        }
    }
}
