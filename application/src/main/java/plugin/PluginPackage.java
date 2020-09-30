package plugin;

import codex.type.IComplexType;
import manager.xml.VersionsDocument;
import org.apache.xmlbeans.XmlException;
import org.atteo.classindex.ClassIndex;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class PluginPackage {

    private final static String VERSION_RESOURCE = "version.xml";

    static final Comparator<String> VER_COMPARATOR = (ver1, ver2) -> {
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
    static final Comparator<PluginPackage> PKG_COMPARATOR = (pkg1, pkg2) -> VER_COMPARATOR.compare(pkg1.version, pkg2.version);

    static Attributes getAttributes(File jarFile) throws IOException {
        try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jarFile))) {
            if (jarStream.getManifest() == null) {
                throw new IOException();
            }
            return jarStream.getManifest().getMainAttributes();
        }
    }

    private final String  vendor, title, version, author;
    private final Boolean build;
    private final URLConnection connection;
    private VersionsDocument versionInfo;

    private final List<PluginHandler<? extends IPlugin>> plugins;

    PluginPackage(File jarFile) throws IOException {
        connection = jarFile.toURI().toURL().openConnection();
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);

        Attributes attributes = getAttributes(jarFile);
        vendor   = IComplexType.coalesce(
                attributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR),
                attributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR_ID)
        );
        title    = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        version  = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        author   = attributes.getValue("Built-By");
        build    = attributes.getValue("Build").equals("true");
        plugins  = loadPlugins();

        try {
            try (URLClassLoader jarLoader = new URLClassLoader(new URL[]{ connection.getURL() }, null)) {
                if (jarLoader.getResource(VERSION_RESOURCE) != null) {
                    versionInfo = VersionsDocument.Factory.parse(jarLoader.getResourceAsStream(VERSION_RESOURCE));
                }
            }
        } catch (XmlException | IOException e) {
            versionInfo = null;
        }
    }

    boolean validatePackage() {
        return vendor != null && title != null && version != null && versionInfo != null;
    }

    String getId() {
        return MessageFormat.format("{0}.{1}", getVendor(), getTitle());
    }

    String getVendor() {
        return vendor;
    }

    String getTitle() {
        return title;
    }

    String getVersion() {
        return version;
    }

    String getAuthor() {
        return author;
    }

    Boolean inDevelopment() {
        return build;
    }

    VersionsDocument getChanges() {
        return versionInfo;
    }

    String getCheckSum() throws Exception {
        byte[] b = Files.readAllBytes(Paths.get(getUrl().toURI()));
        byte[] hash = MessageDigest.getInstance("MD5").digest(b);
        return DatatypeConverter.printHexBinary(hash);
    }

    private List<PluginHandler<? extends IPlugin>> loadPlugins() throws IOException {
        try (URLClassLoader fileLoader = new URLClassLoader(new URL[]{ connection.getURL() }, null)) {
            return StreamSupport.stream(ClassIndex.getAnnotatedNames(Pluggable.class, fileLoader).spliterator(), false)
                    .map(className -> {
                        try {
                            PluginClassLoader pluginLoader = new PluginClassLoader(new URL[]{ connection.getURL() }, className);
                            Class<?> pluginClass = pluginLoader.loadClass(className);
                            Class<? extends PluginHandler> pluginHandlerClass = pluginClass.getAnnotation(Pluggable.class).pluginHandlerClass();
                            Constructor<? extends PluginHandler> handlerConstructor = pluginHandlerClass.getDeclaredConstructor(Class.class);
                            handlerConstructor.setAccessible(true);
                            return (PluginHandler<? extends IPlugin>) handlerConstructor.newInstance(pluginClass);
                        } catch (
                                ClassNotFoundException |
                                IllegalAccessException |
                                InstantiationException |
                                NoSuchMethodException  |
                                InvocationTargetException e
                        ) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    List<PluginHandler<? extends IPlugin>> getPlugins() {
        return new LinkedList<>(plugins);
    }

    URL getUrl() {
        return connection.getURL();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginPackage that = (PluginPackage) o;
        return Objects.equals(vendor, that.vendor) && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendor, title);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0}.{1}-{2}", vendor, title, version);
    }

}
