package plugin;

import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.utils.Language;
import manager.upgrade.UpgradeService;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import org.atteo.classindex.IndexSubclasses;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

@IndexSubclasses
interface IPluginRegistry extends INode {

    String  getTitle();
    default Collection<PackageDescriptor> getPackages() {
        Collection<PackageDescriptor> packages = filterPackages(readPackages());
        if (!packages.isEmpty()) {
            Logger.getContextLogger(PluginProvider.class).debug(
                    "{0} registry packages: \n{1}",
                    getTitle(),
                    packages.stream()
                            .map(descriptor -> MessageFormat.format(
                                    "[{0}]\n{1}",
                                    descriptor,
                                    descriptor.getPlugins().stream()
                                            .map(handler -> MessageFormat.format(
                                                    " - {0}",
                                                    Language.get(handler.getPluginClass(), "title", Locale.US)
                                            ))
                                            .collect(Collectors.joining("\n"))
                            ))
                            .collect(Collectors.joining("\n"))
            );
        }
        return packages;
    }
    Collection<PackageDescriptor> filterPackages(List<PackageDescriptor> packages);
    List<PackageDescriptor>       readPackages();


    abstract class PackageDescriptor implements Closeable {

        static Version createVersion(String number) {
            Version version = Version.Factory.newInstance();
            version.setNumber(number);
            return version;
        }

        private final URI        fileUri;
        private final String     vendor, title;
        private final Version    version;
        private final Boolean    build;
        private List<PluginHandler<? extends IPlugin>> plugins;

        PackageDescriptor(URI fileUri, String vendor, String title, String version, Boolean build) {
            this.fileUri = fileUri;
            this.vendor  = vendor;
            this.title   = title;
            this.version = createVersion(version);
            this.build   = build;
        }

        public final String  getId() {
            return MessageFormat.format("{0}/{1}", vendor, title);
        }

        public final URI     getUri() {
            return fileUri;
        }

        public final String  getTitle() {
            return title;
        }

        public final String  getVendor() {
            return vendor;
        }

        public final Version getVersion() {
            return version;
        }

        protected abstract VersionsDocument getHistory();

        final List<PluginHandler<? extends IPlugin>> getPlugins() {
            if (plugins == null) {
                plugins = loadPlugins();
            }
            return new LinkedList<>(plugins);
        }

        public final Boolean inDevelopment() {
            return build;
        }

        public abstract void    close() throws IOException;
        public abstract String  checksum();
        public abstract Version compatibleWith();
        public abstract boolean compatibleWith(Version version);
        public final    boolean isNewerThan(PackageDescriptor pkgDescriptor) {
            return UpgradeService.VER_COMPARATOR.compare(getVersion(), pkgDescriptor.getVersion()) > 0;
        }

        protected abstract ClassLoader  getClassLoader(URI uri) throws IOException;
        protected abstract List<String> listPluginClasses() throws IOException;
        protected abstract List<PluginHandler<? extends IPlugin>> loadPlugins();
        protected final    List<PluginHandler<? extends IPlugin>> loadPluginsByUri(URI jarURI) {
            final List<PluginHandler<? extends IPlugin>> handlers = new LinkedList<>();
            try {
                for (String className : listPluginClasses()) {
                    try {
                        final Class<?> pluginClass = getClassLoader(jarURI).loadClass(className);
                        final Class<? extends PluginHandler> handlerClass = pluginClass.getAnnotation(Pluggable.class).pluginHandlerClass();
                        final Constructor<? extends PluginHandler> handlerCtor = handlerClass.getDeclaredConstructor(Class.class, String.class);
                        handlerCtor.setAccessible(true);

                        PluginHandler<? extends IPlugin> pluginHandler = (PluginHandler<? extends IPlugin>) handlerCtor.newInstance(pluginClass, getId());

                        handlers.add(pluginHandler);
                    } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                Logger.getContextLogger(PluginProvider.class).warn("Unable to open package: {0}", e.getMessage());
            }
            return handlers;
        }


        @Override
        public final String toString() {
            return MessageFormat.format("{0}/{1}-{2}", vendor, title, version.getNumber());
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) return true;
            if (
                o == null || !(
                        PackageDescriptor.class.isAssignableFrom(getClass()) &&
                        PackageDescriptor.class.isAssignableFrom(o.getClass())
                )
            ) return false;
            PackageDescriptor that = (PackageDescriptor) o;
            return Objects.equals(vendor, that.vendor) && Objects.equals(title, that.title);
        }

        @Override
        public final int hashCode() {
            return Objects.hash(vendor, title);
        }
    }
}
