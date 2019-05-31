package plugin;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

class PluginLoader {

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
                "PXE: Registered plugin package:\nName:   {0}\nAuthor: {1}\nPlugins:\n{2}",
                pluginPackage,
                pluginPackage.getAuthor(),
                plugins.get(pluginPackage).stream()
                        .map(pluginHandler -> " * ".concat(pluginHandler.getDescription()))
                        .collect(Collectors.joining("\n"))
        );

        plugins.get(pluginPackage).forEach(pluginHandler -> {
            if (pluginHandler.getView().isEnabled()) {
                try {
                    pluginHandler.loadPlugin();
                } catch (PluginException e) {
                    Logger.getLogger().warn("Unable to load plugin ''{0}''\n{1}", Plugin.getId(pluginHandler), Logger.stackTraceToString(e));
                    pluginHandler.getView().setEnabled(false, false);
                }
            }
        });
    }

    void removePluginPackage(PluginPackage pluginPackage, boolean removeFile) throws PluginException, IOException {
        Logger.getLogger().debug("PXE: Start plugin package ''{0}'' removal", pluginPackage);
        for (PluginHandler pluginHandler : plugins.get(pluginPackage)) {
            try {
                if (pluginHandler.getView().isEnabled()) {
                    pluginHandler.unloadPlugin();
                }
            } catch (PluginException e) {
                Logger.getLogger().warn("Unable to unload plugin ''{0}''\n{1}", Plugin.getId(pluginHandler), Logger.stackTraceToString(e));
                throw e;
            }
        }
        plugins.remove(pluginPackage);
        if (removeFile) {
            pluginPackage.close();
            Logger.getLogger().debug("PXE: Remove plugin file ''{0}''", pluginPackage.jarFilePath);
            Files.delete(pluginPackage.jarFilePath);
        }
    }

    private void removeUnusedPlugins() {
        List<String> pluginIDs = plugins.values().stream()
                .flatMap(Collection::stream)
                .map(Plugin::getId)
                .collect(Collectors.toList());

        IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
        CAS.readCatalogEntries(null, Plugin.class).entrySet().stream()
                .filter(entry -> !pluginIDs.contains(entry.getValue()))
                .forEach(entry -> {
                    try {
                        Logger.getLogger().debug("PXE: Remove unused plugin ''{0}'' from database", entry.getValue());
                        CAS.removeClassInstance(Plugin.class, entry.getKey());
                    } catch (Exception e) {
                        Logger.getLogger().warn("Unable to remove unused plugin ''{0}'' from database", entry.getValue());
                    }
                });
    }

}
