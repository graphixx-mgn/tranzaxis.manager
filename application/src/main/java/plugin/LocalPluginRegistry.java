package plugin;

import codex.log.Logger;
import codex.model.EntityDefinition;
import codex.type.IComplexType;
import manager.upgrade.UpgradeService;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.xmlbeans.XmlException;
import org.atteo.classindex.ClassIndex;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;

@EntityDefinition(icon="/images/folder.png")
class LocalPluginRegistry extends PluginRegistry implements IPluginRegistry {

    private static final DirectoryStream.Filter<Path> FILE_FILTER = entry ->
            !Files.isDirectory(entry) &&
            "jar".equals(FilenameUtils.getExtension(entry.toFile().getPath()));

    private static final Function<PackageDescriptor, String> POM_PATH = descriptor -> MessageFormat.format(
            "META-INF/maven/{0}/{1}/pom.xml",
            descriptor.getVendor(),
            descriptor.getTitle()
    );
    private static final Map<URI, PackageDescriptor> CACHE = new HashMap<>();

    static PackageDescriptor getDescriptorByUri(URI uri) {
        return CACHE.computeIfAbsent(uri, s -> new LocalPackageDescriptor(uri) {
            @Override
            public void close() throws IOException {
                CACHE.remove(this.getUri());
                super.close();
            }
        });
    }

    private final File directory;

    LocalPluginRegistry(File directory) {
        super("Local storage");
        this.directory = directory;
    }

    @Override
    public Collection<PackageDescriptor> filterPackages(List<PackageDescriptor> packages) {
        return packages.stream()
            //.filter(descriptor -> !descriptor.getPlugins().isEmpty())
            .collect(Collectors.toMap(
                    Object::hashCode,
                    descriptor -> descriptor,
                    BinaryOperator.maxBy((o1, o2) -> UpgradeService.VER_COMPARATOR.compare(o1.getVersion(), o2.getVersion()))
            )).values();
    }

    @Override
    public List<PackageDescriptor> readPackages() {
        Logger.getContextLogger(PluginProvider.class).debug("Load plugin registry [{0}]: {1}", getTitle(), directory.getAbsolutePath());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath(), FILE_FILTER)) {
            return StreamSupport.stream(stream.spliterator(), false)
                    .map(Path::toUri)
                    .map(LocalPluginRegistry::getDescriptorByUri)
                    .collect(Collectors.toList());
        } catch (IOException ignore) {}
        return Collections.emptyList();
    }


    static class LocalPackageDescriptor extends PackageDescriptor {

        private final static String          VERSION_PATH = "version.xml";
        private final static Attributes.Name VERSION_NAME = new Attributes.Name("Version");
        private final static Attributes.Name BUILD_NAME   = new Attributes.Name("Build");

        static Map<Attributes.Name, Object> getAttributes(File jarFile) {
            Map<Attributes.Name, Object> attributes = new HashMap<>();
            try (final JarFile jar = new JarFile(jarFile)) {
                final ZipEntry manifest = jar.getEntry(JarFile.MANIFEST_NAME);
                try (InputStream inputStream = jar.getInputStream(manifest)) {
                    new Manifest(inputStream).getMainAttributes().forEach((key, value) -> attributes.put((Attributes.Name) key, value));
                }
                final ZipEntry version = jar.getEntry(VERSION_PATH);
                try (InputStream inputStream = jar.getInputStream(version)) {
                    attributes.put(VERSION_NAME, VersionsDocument.Factory.parse(inputStream));
                }
            } catch (IOException | XmlException ignore) {}
            return attributes;
        }

        private final Version    compatibleWith;
        private VersionsDocument history;

        LocalPackageDescriptor(URI fileURI) {
            this(fileURI, getAttributes(new File(fileURI)));
        }

        private LocalPackageDescriptor(URI fileURI, Map<Attributes.Name, Object> attributes) {
            super(
                fileURI,
                IComplexType.coalesce(
                        (String) attributes.get(Attributes.Name.IMPLEMENTATION_VENDOR),
                        (String) attributes.get(Attributes.Name.IMPLEMENTATION_VENDOR_ID)
                ),
                (String) attributes.get(Attributes.Name.IMPLEMENTATION_TITLE),
                (String) attributes.get(Attributes.Name.IMPLEMENTATION_VERSION),
                "true".equals(attributes.get(BUILD_NAME))
            );
            this.history = (VersionsDocument) attributes.get(VERSION_NAME);
            this.compatibleWith = getCompatibleAppVersion();
        }

        private Version getCompatibleAppVersion() {
            try {
                try (final URLClassLoader jarLoader = new URLClassLoader(new URL[]{ getUri().toURL() }, null)) {
                    if (jarLoader.getResource(POM_PATH.apply(this)) != null) {
                        final Model model = new MavenXpp3Reader().read(jarLoader.getResourceAsStream(POM_PATH.apply(this)));
                        return PackageDescriptor.createVersion(model.getProperties().getProperty("compatible"));
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected VersionsDocument getHistory() {
            return history;
        }

        @Override
        public void close() throws IOException {
            for (PluginHandler pluginHandler : getPlugins()) {
                Plugin plugin = pluginHandler.getView();
                if (plugin.isEnabled()) {
                    Logger.getContextLogger(PluginProvider.class).debug("Disable plugin: "+pluginHandler.pluginClass);
                    plugin.getCommand(Plugin.UnloadPlugin.class).execute(plugin, Collections.emptyMap());
                }
                Logger.getContextLogger(PluginProvider.class).debug("Close class ''{0}'' loader", pluginHandler.getPluginClass().getTypeName());
                ((URLClassLoader) pluginHandler.getPluginClass().getClassLoader()).close();
            }
        }

        @Override
        public String checksum() {
            try {
                byte[] file = Files.readAllBytes(Paths.get(getUri()));
                byte[] hash = MessageDigest.getInstance("MD5").digest(file);
                return DatatypeConverter.printHexBinary(hash);
            } catch (IOException | NoSuchAlgorithmException e) {
                return null;
            }
        }

        @Override
        public Version compatibleWith() {
            return compatibleWith;
        }

        @Override
        public boolean compatibleWith(Version version) {
            return UpgradeService.VER_COMPARATOR.compare(version, compatibleWith) >= 0;
        }

        @Override
        protected ClassLoader getClassLoader(URI uri) throws IOException {
            URLConnection connection = uri.toURL().openConnection();
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            return new URLClassLoader(new URL[]{ connection.getURL() });
        }

        @Override
        protected List<String> listPluginClasses() throws IOException {
            final ClassLoader listCl = new PluginClassLoader(getUri(), null) {
                @Override
                protected URLConnection getConnection(URI uri) throws IOException {
                    final URLConnection connection = super.getConnection(uri);
                    connection.setUseCaches(false);
                    connection.setDefaultUseCaches(false);
                    return connection;
                }
            };
            return StreamSupport.stream(ClassIndex.getAnnotatedNames(Pluggable.class, listCl).spliterator(), false)
                    .collect(Collectors.toList());
        }

        @Override
        protected final List<PluginHandler<? extends IPlugin>> loadPlugins() {
            return loadPluginsByUri(getUri());
        }
    }

}
