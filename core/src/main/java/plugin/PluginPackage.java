package plugin;

import codex.log.Logger;
import org.atteo.classindex.ClassIndex;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PluginPackage implements Closeable {

    static final Comparator<PluginPackage> PKG_COMPARATOR = (pkg1, pkg2) -> {
        String[] vals1 = pkg1.version.split("\\.");
        String[] vals2 = pkg2.version.split("\\.");

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

    static Attributes getAttributes(File jarFile) throws IOException {
        try (
                JarInputStream jarStream = new JarInputStream(new FileInputStream(jarFile))
        ) {
            if (jarStream.getManifest() == null) {
                throw new IOException();
            }
            return jarStream.getManifest().getMainAttributes();
        }
    }

    final Path jarFilePath;
    final URLClassLoader classLoader;
    private final List<PluginHandler>  pluginList;
    private final String vendor, title, version, author;

    PluginPackage(File jarFile) throws IOException {
        this.jarFilePath = jarFile.toPath();

        Attributes attributes = getAttributes(jarFile);
        vendor  = attributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR_ID);
        title   = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        version = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        author  = attributes.getValue("Built-By");

        URLConnection conn = jarFile.toURI().toURL().openConnection();
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);

        classLoader = new URLClassLoader(new URL[]{ conn.getURL() });
        pluginList = loadPlugins(conn.getURL());
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

    List<PluginHandler> getPlugins() {
        return pluginList;
    }

    int size() {
        return pluginList.size();
    }

    private List<PluginHandler> loadPlugins(URL url) {
        return StreamSupport.stream(ClassIndex.getAnnotatedNames(Pluggable.class, new URLClassLoader(new URL[]{ url }, null)).spliterator(), false)
                .map(className -> {
                    try {
                        Class<?> pluginClass = classLoader.loadClass(className);
                        Class<? extends PluginHandler> pluginHandlerClass = pluginClass.getAnnotation(Pluggable.class).pluginHandlerClass();
                        Constructor<? extends PluginHandler> handlerConstructor = pluginHandlerClass.getDeclaredConstructor(Class.class);
                        handlerConstructor.setAccessible(true);
                        return handlerConstructor.newInstance(pluginClass);
                    } catch (
                            ClassNotFoundException  |
                            IllegalAccessException  |
                            InstantiationException  |
                            NoSuchMethodException   |
                            InvocationTargetException e
                    ) {
                        Logger.getLogger().error("Error", e);
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginPackage that = (PluginPackage) o;
        return vendor.equals(that.vendor) && title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendor, title);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0}.{1}-{2}", vendor, title, version);
    }

    @Override
    public void close() throws IOException {
        classLoader.close();
    }

}
