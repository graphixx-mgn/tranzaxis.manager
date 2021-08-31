package plugin;

import java.io.*;
import java.net.*;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class PluginClassLoader extends URLClassLoader implements AutoCloseable {

    private final Map<String, byte[]> entries;
    private final ProtectionDomain    domain;

//    PluginClassLoader(URI jarUri) throws IOException {
//        super(new URL[]{});
//        this.domain  = new ProtectionDomain(new CodeSource(jarUri.toURL(), (Certificate[]) null), null, this, null);
//        this.entries = readEntries(jarUri);
//    }

    PluginClassLoader(URI jarUri, ClassLoader parent) throws IOException {
        super(new URL[]{}, parent);
        this.domain  = new ProtectionDomain(new CodeSource(jarUri.toURL(), (Certificate[]) null), null, this, null);
        this.entries = readEntries(jarUri);
    }

    @Override
    public Class findClass(String name) throws ClassNotFoundException {
        final String entryName = convertName(name);
        if (entries.containsKey(entryName)) {
            final byte[] bytes = entries.get(entryName);
            return defineClass(name, bytes, 0, bytes.length, domain);
        } else {
            return super.findClass(name);
        }
    }

    @Override
    public final URL findResource(String name) {
        return entries.containsKey(name) ? getUrl(name) : super.findResource(name);
    }

    @Override
    public final Enumeration<URL> findResources(String name) throws IOException {
        final List<URL> urls = entries.keySet().stream()
                .filter(path -> path.startsWith(name))
                .map(path -> getUrl(name))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (urls.isEmpty()) {
            return super.findResources(name);
        } else {
            return Collections.enumeration(urls);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return entries.containsKey(name) ?
                new ByteArrayInputStream(entries.get(name)) :
                super.getResourceAsStream(name);
    }

    protected URLConnection getConnection(URI uri) throws IOException {
        return createConnection(uri);
    }

    private String convertName(String className) {
        return className.replace('.', '/') + ".class";
    }

    private URL getUrl(String entryName) {
        try {
            return new URL(null, "byte:" + entryName, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) {
                    return new URLConnection(u) {
                        public void connect() {}

                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(entries.get(entryName));
                        }
                    };
                }
            });
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private Map<String, byte[]> readEntries(URI uri) throws IOException {
        final Map<String, byte[]> entries = new HashMap<>();
        try (final ZipInputStream zip = new ZipInputStream(getConnection(uri).getInputStream())) {
            ZipEntry entry;
            do {
                entry = zip.getNextEntry();
                if (entry != null && entry.getSize() > 0) {
                    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    int nextValue;
                    try {
                        while ((nextValue = zip.read()) != -1) {
                            byteStream.write(nextValue);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    entries.put(entry.getName(), byteStream.toByteArray());
                }
            } while (entry != null);
        }
        return entries;
    }

    private static URLConnection createConnection(URI uri) throws IOException {
        return uri.toURL().openConnection();
    }
}
