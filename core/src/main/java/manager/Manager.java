package manager;

import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.InstanceUnit;
import codex.launcher.LauncherUnit;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.model.Bootstrap;
import codex.service.ServiceUnit;
import codex.task.TaskManager;
import codex.utils.ImageUtils;
import codex.utils.Language;
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import manager.nodes.Common;
import manager.nodes.DatabaseRoot;
import manager.nodes.EnvironmentRoot;
import manager.nodes.RepositoryRoot;
import manager.type.Locale;
import manager.ui.Window;
import manager.ui.splash.SplashScreen;
import manager.upgrade.UpgradeUnit;
import plugin.PluginManager;
import sun.util.logging.PlatformLogger;
import javax.swing.*;

public class Manager {
    
    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            //
        }
        PlatformLogger.getLogger("java.util.prefs").setLevel(PlatformLogger.Level.OFF);
    }

    public static void main(String[] args) {
        new Manager();
    }

    public Manager() {
//        new NotifyMessage("Test", "TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST", 0, null).setVisible(true);
        SplashScreen splash = new SplashScreen();
        splash.setProgress(0, "Load system services");
        loadSystemProps();
        Window window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        String uniqueAppId = Manager.class.getCanonicalName();
        try {
            JUnique.acquireLock(uniqueAppId, (message) -> {
                window.setState(JFrame.NORMAL);
                window.setExtendedState(window.getExtendedState());
                window.toFront();
                window.requestFocus();
                return null;
            });
        } catch (AlreadyLockedException e) {
            JUnique.sendMessage(uniqueAppId, "OPEN");
            System.exit(0);
        }
        splash.setProgress(10, "Initialize logging unit");
        LogUnit logViewer = LogUnit.getInstance();

        splash.setProgress(20, "Load plugin manager");
        PluginManager pluginManager = PluginManager.getInstance();

        splash.setProgress(40, "Start task management system");
        TaskManager taskManager = new TaskManager();

        splash.setProgress(50, "Build configuration root");
        Common root = new Common();
        NodeTreeModel objectsTree = new NodeTreeModel(root);
        ExplorerUnit configExplorer = ExplorerUnit.getInstance();
        configExplorer.setModel(objectsTree);

        root.insert(new RepositoryRoot());
        root.insert(new DatabaseRoot());
        root.insert(new EnvironmentRoot());

        splash.setProgress(70, "Start command launcher unit");
        LauncherUnit commandLauncher = new LauncherUnit();

        splash.setProgress(80, "Start service management unit");
        ServiceUnit serviceOptions = new ServiceUnit();

        splash.setProgress(90, "Start instance control unit");
        InstanceUnit networkBrowser = InstanceUnit.getInstance();

        splash.setProgress(90, "Start upgrade unit");
        UpgradeUnit upgradeUnit = new UpgradeUnit();

        splash.setProgress(100, "Initialize user interface");
        window.addUnit(logViewer,   window.loggingPanel);
        window.addUnit(upgradeUnit, window.upgradePanel);
        window.addUnit(taskManager, window.taskmgrPanel);

        window.addUnit(configExplorer);
        window.addUnit(commandLauncher);
        window.addUnit(serviceOptions);
        window.addUnit(networkBrowser);
        window.addUnit(pluginManager);

        splash.setVisible(false);
        window.setVisible(true);
    }
    
    private void loadSystemProps() {
        String PID  = Language.get(Common.class, "title", new java.util.Locale("en", "US"));
        String lang = Bootstrap.getProperty(Common.class, PID, "guiLang");
        if (lang != null) {
            java.util.Locale locale = Locale.valueOf(lang).getLocale();
            java.lang.System.setProperty("user.language", locale.getLanguage());
            java.lang.System.setProperty("user.country",  locale.getCountry());
        }
        java.util.Locale guiLocale = Language.getLocale();
        Logger.getLogger().debug("" +
                "GUI locale:\n * Language: {0} \n * Country:  {1}",
                guiLocale.getDisplayLanguage(),
                guiLocale.getDisplayCountry()
        );
    }

}
