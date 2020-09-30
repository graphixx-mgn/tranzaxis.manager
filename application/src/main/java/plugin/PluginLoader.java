package plugin;

import codex.context.IContext;
import codex.log.Level;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.task.TaskOutput;
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
public class PluginLoader implements IContext {

    private static final File PLUGIN_DIR = new File("plugins");
    private final static FileFilter FILE_FILTER = file -> file.isFile() && file.getName().endsWith(".jar");

    private final List<PluginPackage> packages = new LinkedList<>();
    private final List<ILoaderListener> listeners = new LinkedList<>();

    static {
        PLUGIN_DIR.mkdirs();
    }

    static Throwable getCause(Throwable exception) {
        Throwable throwable = exception;
        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    PluginLoader() {
        findPluginPackages(PLUGIN_DIR);
        //removeUnusedPlugins();
    }

    File getPluginDir() {
        return PLUGIN_DIR;
    }

    private void findPluginPackages(File pluginDir) {
        File[] jarFiles = pluginDir.listFiles(FILE_FILTER);
        if (jarFiles != null) {
            BinaryOperator<PluginPackage> maxVersion = BinaryOperator.maxBy(PluginPackage.PKG_COMPARATOR);

            Arrays.stream(jarFiles)
                    .map(jarFile -> {
                        try {
                            PluginPackage pluginPackage = new PluginPackage(jarFile);
                            return pluginPackage.validatePackage() ? pluginPackage : null;
                        } catch (Throwable e) {
                            Logger.getContextLogger(PluginLoader.class).warn(
                                    MessageFormat.format("Unable to load plugin package ''{0}''", jarFile),
                                    e
                            );
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(pluginPackage -> {
                        if (pluginPackage.getPlugins().isEmpty()) {
                            Logger.getContextLogger(PluginLoader.class).warn(
                                    "Plugin package ''{0}'' does not contain supportable plugins",
                                    pluginPackage.getTitle()
                            );
                        }
                        return pluginPackage.getPlugins().size() > 0;
                    })
                    .collect(Collectors.toMap(
                            PluginPackage::hashCode,
                            pluginPackage -> pluginPackage,
                            maxVersion
                    ))
                    .forEach((integer, pluginPackage) -> registerPluginPackage(pluginPackage));
        }
    }

    private void registerPluginPackage(PluginPackage pluginPackage) {
        if (packages.contains(pluginPackage)) {
            replacePluginPackage(pluginPackage);
        } else {
            addPluginPackage(pluginPackage);
        }
    }

    void addPluginPackage(PluginPackage pluginPackage) {
        packages.add(pluginPackage);
        SwingUtilities.invokeLater(() -> {
            Logger.getLogger().debug(
                    "Registered plugin package ''{0}''. Plugins:\n{1}",
                    pluginPackage,
                    pluginPackage.getPlugins().stream()
                            .map(pluginHandler -> " * ".concat(pluginHandler.toString()))
                            .collect(Collectors.joining("\n"))
            );
            pluginPackage.getPlugins().forEach(pluginHandler -> {
                if (pluginHandler.getView().isEnabled() && pluginHandler.getView().isOptionsValid()) {
                    try {
                        pluginHandler.loadPlugin();
                    } catch (PluginException e) {
                        Logger.getLogger().warn(MessageFormat.format("Unable to load plugin ''{0}''", Plugin.getId(pluginHandler)), e);
                        pluginHandler.getView().setEnabled(false, false);
                    }
                }
            });
            new LinkedList<>(listeners).forEach(listener -> listener.packageLoaded(pluginPackage));
        });
    }

    void removePluginPackage(PluginPackage pluginPackage, boolean unload, boolean removeFile) throws PluginException, IOException {
        Logger.getLogger().debug("Remove plugin package ''{0}''", pluginPackage);
        if (unload) {
            for (PluginHandler pluginHandler : pluginPackage.getPlugins()) {
                if (pluginHandler.getView().isEnabled()) {
                    if (!pluginHandler.unloadPlugin()) return;
                }
            }
        }
        if (removeFile) {
            for (PluginHandler pluginHandler : pluginPackage.getPlugins()) {
                Logger.getLogger().debug("Close class ''{0}'' loader", pluginHandler.getPluginClass().getTypeName());
                ((PluginClassLoader) pluginHandler.getPluginClass().getClassLoader()).close();
            }
            File packageFile = new File(pluginPackage.getUrl().getFile());
            Logger.getLogger().debug("Remove plugin file ''{0}''", packageFile);
            Files.delete(packageFile.toPath());
        }
        packages.remove(pluginPackage);
    }

    void replacePluginPackage(PluginPackage pluginPackage) {
        PluginPackage installedPackage = getPackageById(pluginPackage.getId());
        Logger.getLogger().debug(
                "Update plugin package ''{0}'' ({1} -> {2})",
                pluginPackage.getId(),
                installedPackage.getVersion(),
                pluginPackage.getVersion()
        );

        boolean reloaded = true;
        for (PluginHandler<? extends IPlugin> installedPlugin : installedPackage.getPlugins()) {
            if (installedPlugin.getView().isEnabled()) {
                PluginHandler<? extends IPlugin> newPlugin = pluginPackage.getPlugins().get(pluginPackage.getPlugins().indexOf(installedPlugin));
                Logger.getLogger().debug(MessageFormat.format("Reload plugin ''{0}''", Plugin.getId(newPlugin)));
                try {
                    //noinspection unchecked
                    reloaded = reloaded && installedPlugin.reloadPlugin((PluginHandler) newPlugin);
                    TaskOutput.put(
                            Level.Debug,
                            DownloadPackages.fillStepResult(
                                MessageFormat.format(DownloadPackages.STEP_RELOAD, newPlugin.getTitle()),
                                null, null
                        )
                    );
                } catch (PluginException e) {
                    Logger.getLogger().warn(MessageFormat.format("Unable to reload plugin ''{0}''", Plugin.getId(newPlugin)), e);
                    TaskOutput.put(
                            Level.Warn,
                            DownloadPackages.fillStepResult(
                                    MessageFormat.format(DownloadPackages.STEP_RELOAD, newPlugin.getTitle()),
                                    null, e
                            )
                    );
                }
            }
        }
        if (reloaded) {
            try {
                removePluginPackage(installedPackage, false, true);
                TaskOutput.put(
                        Level.Debug,
                        DownloadPackages.fillStepResult(DownloadPackages.STEP_REMOVE, null, null)
                );
            } catch (PluginException | IOException e) {
                TaskOutput.put(
                        Level.Warn,
                        DownloadPackages.fillStepResult(DownloadPackages.STEP_REMOVE, null, e)
                );
            }
        }
        packages.add(pluginPackage);
        new LinkedList<>(listeners).forEach(listener -> listener.packageLoaded(pluginPackage));
    }

//    private void removeUnusedPlugins() {
//        List<String> pluginIDs = plugins.values().stream()
//                .flatMap(Collection::stream)
//                .map(Plugin::getId)
//                .collect(Collectors.toList());
//
//        IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
//        CAS.readCatalogEntries(null, Plugin.class).entrySet().stream()
//                .filter(entry -> !pluginIDs.contains(entry.getValue()))
//                .forEach(entry -> {
//                    try {
//                        Logger.getLogger().debug("Remove unused plugin ''{0}'' from database", entry.getValue());
//                        CAS.removeClassInstance(Plugin.class, entry.getKey());
//                    } catch (Exception e) {
//                        Logger.getLogger().warn(MessageFormat.format("Unable to remove unused plugin ''{0}'' from database", entry.getValue()), e);
//                    }
//                });
//    }

    void addListener(ILoaderListener listener) {
        listeners.add(listener);
    }

    List<PluginPackage> getPackages() {
        return new LinkedList<>(packages);
    }

    PluginPackage getPackageById(String id) {
        return packages.stream().filter(pluginPackage -> pluginPackage.getId().equals(id)).findFirst().orElse(null);
    }


    interface ILoaderListener {
        void packageLoaded(PluginPackage pluginPackage);
    }
}
