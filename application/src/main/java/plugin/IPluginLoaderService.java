package plugin;

import codex.editor.AnyTypeView;
import codex.editor.IEditor;
import codex.instance.Instance;
import codex.model.Access;
import codex.model.EntityModel;
import codex.service.IRemoteService;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.stream.RemoteInputStream;
import manager.xml.VersionsDocument;
import javax.swing.*;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public interface IPluginLoaderService extends IRemoteService {

    @Override
    default String getTitle() throws RemoteException {
        return "Plugin Loader Service";
    }

    List<RemotePackage> getPublishedPackages(Locale locale) throws RemoteException;

    void packagePublicationChanged(RemotePackage remotePackage, boolean published) throws RemoteException;

    String getPackageFileChecksum(String pluginId, String pluginVersion) throws RemoteException;

    RemoteInputStream getPackageFileStream(String pluginId, String pluginVersion)  throws RemoteException;


    class RemotePackage implements Serializable, Iconified {
        private static final long serialVersionUID = -3332763603180744471L;

        private final String vendor, title, version, author;
        private final ImageIcon icon;
        private final VersionsDocument   changes;
        private final List<Instance>     instances = new LinkedList<>();
        private final List<RemotePlugin> plugins = new LinkedList<>();

        RemotePackage(PluginPackage pluginPackage) {
            vendor  = pluginPackage.getVendor();
            title   = pluginPackage.getTitle();
            version = pluginPackage.getVersion();
            changes = pluginPackage.getChanges();
            author  = pluginPackage.getAuthor();
            icon    = PackageView.PACKAGE;

            plugins.addAll(pluginPackage.getPlugins().stream()
                    .map(RemotePlugin::new)
                    .collect(Collectors.toList())
            );
        }

        String getId() {
            return MessageFormat.format("{0}.{1}", vendor, title);
        }

        String getTitle() {
            return title;
        }

        String getVersion() {
            return version;
        }

        VersionsDocument getChanges() {
            return changes;
        }

        String getAuthor() {
            return author;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return MessageFormat.format("{0}.{1}-{2}", vendor, title, version);
        }

        synchronized void addInstance(Instance instance) {
            instances.add(instance);
        }

        synchronized void removeInstance(Instance instance) {
            instances.remove(instance);
        }

        synchronized List<Instance> getInstances() {
            return new LinkedList<>(instances);
        }

        synchronized boolean isAvailable() {
            return !instances.isEmpty();
        }

        List<RemotePlugin> getPlugins() {
            return new LinkedList<>(plugins);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RemotePackage that = (RemotePackage) o;
            return vendor.equals(that.vendor) &&
                    title.equals(that.title) &&
                    version.equals(that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vendor, title, version);
        }
    }


    class RemotePlugin implements Serializable {
        private static final long serialVersionUID = 1808014886861935479L;

        private final String pluginId;
        private final Class<? extends PluginHandler> handlerClass;
        private final List<PropertyPresentation> properties = new LinkedList<>();

        RemotePlugin(PluginHandler<? extends IPlugin> pluginHandler) {
            handlerClass = pluginHandler.getClass();
            pluginId     = Plugin.getId(pluginHandler);

            properties.add(new PropertyPresentation(pluginHandler));
            pluginHandler.getView().getProperties().forEach(propName -> properties.add(new PropertyPresentation(pluginHandler, propName)));
        }

        String getPluginId() {
            return pluginId;
        }

        Class<? extends PluginHandler> getHandlerClass() {
            return handlerClass;
        }

        public List<PropertyPresentation> getProperties() {
            return new LinkedList<>(properties);
        }

    }


    class PropertyPresentation implements Iconified, Serializable {
        private static final long serialVersionUID = 7929411017083767918L;

        private final String    editor;
        private final String    name, value;
        private final ImageIcon icon;
        private final Access    access;

        PropertyPresentation(final PluginHandler pluginHandler) {
            name   = EntityModel.THIS;
            value  = Language.get(pluginHandler.pluginClass, "title");
            icon   = ImageUtils.getByPath(pluginHandler.pluginClass, Language.get(pluginHandler.pluginClass, "icon"));
            editor = AnyTypeView.class.getTypeName();
            access = Access.Edit;
        }

        PropertyPresentation(final PluginHandler pluginHandler, final String propName) {
            final EntityModel model = pluginHandler.getView().model;

            Object propVal = model.calculateDynamicValue(propName);
            name   = propName;
            value  = propVal == null ? null : propVal.toString();
            icon   = propVal == null || !Iconified.class.isAssignableFrom(propVal.getClass()) ? null : ((Iconified) propVal).getIcon();
            editor = model.getEditor(propName).getClass().getTypeName();

            boolean shownInSelector = model.getProperties(Access.Select).contains(propName);
            boolean shownInEditor   = model.getProperties(Access.Edit).contains(propName);

            if (shownInSelector && shownInEditor) {
                access = null;
            } else if (shownInSelector) {
                access = Access.Edit;
            } else if (shownInEditor) {
                access = Access.Select;
            } else {
                access = Access.Any;
            }
        }

        public String getName() {
            return name;
        }

        public Access getAccess() {
            return access;
        }

        public String getValue() {
            return value;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        public Class<? extends IEditor> getEditor() {
            try {
                return Class.forName(editor).asSubclass(IEditor.class);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    interface IPublicationListener {
        void publicationEvent(RemotePackage remotePackage, boolean published);
    }
}
