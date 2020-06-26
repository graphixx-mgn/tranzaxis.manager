package plugin;

import java.net.URL;
import java.net.URLClassLoader;

class PluginClassLoader extends URLClassLoader {

    private final String className;

    PluginClassLoader(URL[] urls, String className) {
        super(urls);
        this.className = className;
    }
}
