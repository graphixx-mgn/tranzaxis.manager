package plugin;


import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.Attributes;

public class Plugin extends Catalog {

    private final static ImageIcon DISABLED = ImageUtils.getByPath("/images/unavailable.png");

    private final static String PROP_TYPE    = "type";
    private final static String PROP_STATUS  = "status";
    private final static String PROP_ENABLED = "enabled";
    private final static String PROP_TYPEDEF = "typedef";

    private final Supplier<PluginHandler> pluginHandler;

    private Plugin(EntityRef owner, String title) {
        this(null);
    }

    public Plugin(PluginHandler pluginHandler) {
        super(
                null,
                new ImageIcon(),
                pluginHandler == null ? null : getId(pluginHandler),
                null
        );
        this.pluginHandler = () -> pluginHandler;

        model.addDynamicProp(PROP_TYPE, new AnyType(), Access.Select, () -> pluginHandler);
        model.addDynamicProp(PROP_STATUS, new AnyType(), Access.Edit, pluginHandler == null ? null : () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return isEnabled() ? pluginHandler.getIcon() : ImageUtils.combine(ImageUtils.grayscale(pluginHandler.getIcon()), DISABLED);
            }

            @Override
            public final String toString() {
                return pluginHandler.toString();
            }
        }, PROP_ENABLED);
        model.addDynamicProp(PROP_TYPEDEF, new AnyType(), Access.Edit, pluginHandler == null ? null : pluginHandler::getTypeDefinition);
        model.addUserProp(PROP_ENABLED, new Bool(false), false, Access.Any);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }

    static String getId(PluginHandler pluginHandler) {
        try {
            Attributes attributes = PluginPackage.getAttributes(new File(
                    ((URLClassLoader) pluginHandler.pluginClass.getClassLoader()).getURLs()[0].getFile()
            ));
            return MessageFormat.format(
                    "{0}.{1}/{2}",
                    attributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR_ID),
                    attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE),
                    pluginHandler.pluginClass.getCanonicalName().toLowerCase()
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    ImageIcon getStatusIcon() {
        return isEnabled() ?
               pluginHandler.get().getIcon() :
               ImageUtils.combine(ImageUtils.grayscale(pluginHandler.get().getIcon()), DISABLED);
    }

    final void setEnabled(boolean value, boolean commit) {
        model.setValue(PROP_ENABLED, value);
        if (commit) {
            try {
                model.commit(false);
            } catch (Exception e) {
                //
            }
        }
    }

    final boolean isEnabled() {
        return model.getUnsavedValue(PROP_ENABLED) == Boolean.TRUE;
    }


    class LoadPlugin extends EntityCommand<Plugin> {

        LoadPlugin() {
            super(
                    "load plugin",
                    Language.get(Plugin.class, "load@title"),
                    ImageUtils.getByPath("/images/start.png"),
                    Language.get(Plugin.class, "load@title"),
                    pluginOptions -> !pluginOptions.isEnabled()
            );
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

        @Override
        public void execute(Plugin context, Map<String, IComplexType> params) {
            PluginHandler handler = pluginHandler.get();
            try {
                handler.loadPlugin();
                setEnabled(true, true);
            } catch (PluginException e) {
                String pluginId = getId(handler);
                Logger.getLogger().warn("Unable to load plugin ''{0}''\n{1}", pluginId, Logger.stackTraceToString(e));
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(Plugin.class, "load@error"),
                                e.getMessage()
                        )
                );
            }
            context.fireChangeEvent();
        }
    }

    class UnloadPlugin extends EntityCommand<Plugin> {

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
            PluginHandler handler = pluginHandler.get();
            try {
                handler.unloadPlugin();
                setEnabled(false, true);
            } catch (PluginException e) {
                String pluginId = getId(handler);
                Logger.getLogger().warn("Unable to unload plugin ''{0}''\n{1}", pluginId, Logger.stackTraceToString(e));
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(Plugin.class, "unload@error"),
                                e.getMessage()
                        )
                );
            }
            context.fireChangeEvent();
        }
    }
}
