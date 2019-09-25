package plugin;

import codex.config.IConfigStoreService;
import codex.context.IContext;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.service.ServiceRegistry;
import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@LoggingSource
@IContext.Definition(id = "PXE", name = "Pluggable Extensions Manager", icon = "/images/plugins.png")
public class PluginLoader implements IContext, PluginHandler.IHandlerListener {

    private final static FileFilter FILE_FILTER = file -> file.isFile() && file.getName().endsWith(".jar");
    private final Map<PluginPackage, List<PluginHandler>> plugins = new HashMap<>();

    PluginLoader(File pluginDir) {
        new Thread(() -> {
            findPluginPackages(pluginDir);
            removeUnusedPlugins();
        }).start();
    }

    private void findPluginPackages(File pluginDir) {
        File[] jars = pluginDir.listFiles(FILE_FILTER);
        if (jars != null) {
            BinaryOperator<PluginPackage> maxVersion = BinaryOperator.maxBy(PluginPackage.PKG_COMPARATOR);

            registerPluginPackages(
                    Arrays.stream(jars)
                            .map(this::loadPluginPackage)
                            .filter(pluginPackage -> pluginPackage != null && pluginPackage.size() > 0)
                            .collect(Collectors.toMap(
                                    PluginPackage::hashCode,
                                    pluginPackage -> pluginPackage,
                                    maxVersion
                            )).values()
                            .toArray(new PluginPackage[0])
            );
        }
    }

    private void registerPluginPackages(PluginPackage... newPackages) {
        if (newPackages != null && newPackages.length > 0) {
            for (PluginPackage newPackage : newPackages) {
                if (!plugins.containsKey(newPackage)) {
                    addPluginPackage(newPackage);
                } else {
                    plugins.keySet().stream()
                            .filter(existPackage -> existPackage.equals(newPackage))
                            .findFirst()
                            .ifPresent(existPackage -> {
                                try {
                                    removePluginPackage(existPackage, false);
                                    addPluginPackage(newPackage);
                                } catch (PluginException | IOException e) {
                                    e.printStackTrace();
                                }
                            });
                }
            }
        }
    }

    private PluginPackage loadPluginPackage(File jarFile) {
        try {
            return new PluginPackage(jarFile);
        } catch (IOException e) {
            return null;
        }
    }

    void addPluginPackage(PluginPackage pluginPackage) {
        plugins.put(pluginPackage, pluginPackage.getPlugins());
        Logger.getLogger().debug(
                "Registered plugin package ''{0}'':\nAuthor: {1}\nPlugins:\n{2}",
                pluginPackage,
                pluginPackage.getAuthor(),
                plugins.get(pluginPackage).stream()
                        .map(pluginHandler -> " * ".concat(pluginHandler.getDescription()))
                        .collect(Collectors.joining("\n"))
        );

        plugins.get(pluginPackage).forEach(pluginHandler -> {
            pluginHandler.addHandlerListener(this);
            SwingUtilities.invokeLater(() -> {
                if (pluginHandler.getView().isEnabled()) {
                    try {
                        pluginHandler.loadPlugin();
                    } catch (PluginException e) {
                        Logger.getLogger().warn(MessageFormat.format("Unable to load plugin ''{0}''", Plugin.getId(pluginHandler)), e);
                        pluginHandler.getView().setEnabled(false, false);
                    }
                }
            });
        });
    }

    void removePluginPackage(PluginPackage pluginPackage, boolean removeFile) throws PluginException, IOException {
        Logger.getLogger().debug("Start plugin package ''{0}'' removal", pluginPackage);
        for (PluginHandler pluginHandler : plugins.get(pluginPackage)) {
            try {
                if (pluginHandler.getView().isEnabled()) {
                    pluginHandler.unloadPlugin();
                }
            } catch (PluginException e) {
                Logger.getLogger().warn(MessageFormat.format("Unable to unload plugin ''{0}''", Plugin.getId(pluginHandler)), e);
                throw e;
            }
        }
        plugins.remove(pluginPackage);
        if (removeFile) {
            pluginPackage.close();
            Logger.getLogger().debug("Remove plugin file ''{0}''", pluginPackage.jarFilePath);
            Files.delete(pluginPackage.jarFilePath);
        }
    }

    private void removeUnusedPlugins() {
        List<String> pluginIDs = plugins.values().stream()
                .flatMap(Collection::stream)
                .map(Plugin::getId)
                .collect(Collectors.toList());

        IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
        CAS.readCatalogEntries(null, Plugin.class).entrySet().stream()
                .filter(entry -> !pluginIDs.contains(entry.getValue()))
                .forEach(entry -> {
                    try {
                        Logger.getLogger().debug("Remove unused plugin ''{0}'' from database", entry.getValue());
                        CAS.removeClassInstance(Plugin.class, entry.getKey());
                    } catch (Exception e) {
                        Logger.getLogger().warn(MessageFormat.format("Unable to remove unused plugin ''{0}'' from database", entry.getValue()), e);
                    }
                });
    }

    List<PluginPackage> getPackages() {
        return new LinkedList<>(plugins.keySet());
    }

    PluginPackage getPackageById(String id) {
        return plugins.keySet().stream().filter(pluginPackage -> pluginPackage.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public void pluginLoaded(PluginHandler handler) {
        Logger.getLogger().debug("Enabled plugin: {0}", handler.getDescription());
    }

    @Override
    public void pluginUnloaded(PluginHandler handler) {
        Logger.getLogger().debug("Disabled plugin: {0}", handler.getDescription());
    }
}
