package plugin;

import codex.context.IContext;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.model.Entity;
import codex.service.AbstractService;
import org.atteo.classindex.ClassIndex;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@LoggingSource
@IContext.Definition(id = "PXE", name = "Plugin Provider Service", icon = "/images/plugins.png")
public class PluginProvider extends AbstractService<PluginProviderOptions> implements IPluginService, IContext {

    static final File PLUGIN_LOCAL_DIR = new File("plugins");

    private IPluginRegistry       localRegistry;
    private List<IPluginRegistry> otherRegistries = new LinkedList<>();
    private final PackageList     packages        = new PackageList();

    private final List<IPluginServiceListener> listeners = new LinkedList<>();

    static {
        //noinspection ResultOfMethodCallIgnored
        PLUGIN_LOCAL_DIR.mkdirs();
    }

    @Override
    public void startService() {
        super.startService();
        localRegistry = new LocalPluginRegistry(PLUGIN_LOCAL_DIR);

        ClassIndex.getSubclasses(IPluginRegistry.class).forEach(registryClass -> {
            if (!localRegistry.getClass().equals(registryClass)) {
                try {
                    Constructor<? extends IPluginRegistry> ctor = registryClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    IPluginRegistry registry = ctor.newInstance();
                    getSettings().attach(registry);
                    otherRegistries.add(registry);
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    Logger.getLogger().warn("Unable to initialize registry ''{0}''", registryClass);
                }
            }
        });
    }

    @Override
    public Collection<PluginPackage> getPackages() {
        synchronized (packages) {
            return new ArrayList<>(packages);
        }
    }

    @Override
    public void readPackages() {
        new Thread(() -> register(localRegistry.getPackages(), true)).start();
        otherRegistries.forEach(registry -> new Thread(() -> register(registry.getPackages(), false)).start());
    }

    @Override
    public void addListener(IPluginServiceListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(IPluginServiceListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    void register(Collection<IPluginRegistry.PackageDescriptor> descriptorList, final boolean local) {
        synchronized (packages) {
            // Handle found packages
            descriptorList.forEach(descriptor -> {
                PluginPackage pluginPackage = Entity.newInstance(PluginPackage.class, null, descriptor.getTitle());
                if (pluginPackage != null) {
                    if (local) {
                        pluginPackage.setLocalDescriptor(descriptor);
                    } else {
                        pluginPackage.setRemoteDescriptor(descriptor);
                    }
                }
                if (packages.add(pluginPackage)) synchronized (listeners) {
                    listeners.forEach(listener -> listener.packageRegistered(packages.indexOf(pluginPackage), pluginPackage));
                }
            });
            // Search obsolete descriptors
            Collection<PluginPackage> obsolete = packages.stream()
                    .filter(pluginPackage -> {
                        final IPluginRegistry.PackageDescriptor descriptor = local ? pluginPackage.getLocal() : pluginPackage.getRemote();
                        return descriptor != null && !descriptorList.contains(descriptor);
                    })
                    .collect(Collectors.toList());
            if (!obsolete.isEmpty()) {
                unregister(obsolete, local);
            }
        }
    }

    void unregister(Collection<PluginPackage> packageList, final boolean local) {
        synchronized (packages) {
            packageList.forEach(pluginPackage -> {
                if (local) {
                    IPluginRegistry.PackageDescriptor descriptor = pluginPackage.getLocal();
                    for (PluginHandler pluginHandler : descriptor.getPlugins()) {
                        Plugin plugin = pluginHandler.getView();
                        if (plugin.getID() != null) {
                            Logger.getContextLogger(PluginProvider.class).debug("Remove plugin entity: "+plugin.model.getQualifiedName());
                            plugin.model.remove();
                        }
                    }
                    try {
                        descriptor.close();
                        File packageFile = new File(descriptor.getUri());
                        Logger.getContextLogger(PluginProvider.class).debug("Remove plugin file ''{0}''", packageFile);
                        Files.delete(packageFile.toPath());
                        pluginPackage.setLocalDescriptor(null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    pluginPackage.setRemoteDescriptor(null);
                }
                int index = packages.indexOf(pluginPackage);
                if (pluginPackage.getPackageId() == null) {
                    if (packages.remove(pluginPackage)) {
                        listeners.forEach(listener -> listener.packageUnregistered(index, pluginPackage));
                    }
                }
            });
        }
    }

    void reload(IPluginRegistry.PackageDescriptor oldDescriptor, IPluginRegistry.PackageDescriptor newDescriptor) throws IOException, PluginException {
        synchronized (packages) {
            if (oldDescriptor != null) {
                for (PluginHandler plugin : oldDescriptor.getPlugins()) {
                    if (plugin.getView().isEnabled()) {
                        Logger.getContextLogger(PluginProvider.class).debug("Unload plugin: {0}", plugin.pluginClass);
                        plugin.unloadPlugin();
                    }
                    Logger.getContextLogger(PluginProvider.class).debug("Close class ''{0}'' loader", plugin.getPluginClass().getTypeName());
                    ((URLClassLoader) plugin.getPluginClass().getClassLoader()).close();
                }
                File packageFile = new File(oldDescriptor.getUri());
                Logger.getContextLogger(PluginProvider.class).debug("Remove plugin file ''{0}''", packageFile);
                Files.delete(packageFile.toPath());
            }
            PluginPackage pluginPackage = Entity.newInstance(PluginPackage.class, null, newDescriptor.getTitle());
            if (pluginPackage != null) {
                pluginPackage.setLocalDescriptor(newDescriptor);
            }
            if (packages.add(pluginPackage)) synchronized (listeners) {
                listeners.forEach(listener -> listener.packageRegistered(packages.indexOf(pluginPackage), pluginPackage));
            }
        }
    }


    private static class PackageList extends LinkedList<PluginPackage> {

        private final Comparator<PluginPackage> comparator = Comparator.comparing(PluginPackage::getPackageId);

        @Override
        public boolean addAll(Collection<? extends PluginPackage> c) {
            try {
                return super.addAll(c);
            } finally {
                this.sort(this.comparator);
            }
        }
        @Override
        public void add(int index, PluginPackage element) {
            add(element);
        }
        @Override
        public boolean add(PluginPackage pluginPackage) {
            try {
                return !contains(pluginPackage) && super.add(pluginPackage);
            } finally {
                this.sort(this.comparator);
            }
        }
    }
}
