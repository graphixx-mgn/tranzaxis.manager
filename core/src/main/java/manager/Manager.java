package manager;

import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.InstanceUnit;
import codex.launcher.LauncherUnit;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.service.ServiceUnit;
import codex.task.TaskManager;
import codex.utils.ImageUtils;
import codex.utils.Language;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
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
import java.util.prefs.Preferences;

public class Manager {
    
    static {
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
            UIManager.put("Tree.drawDashedFocusIndicator", false);
        } catch (UnsupportedLookAndFeelException e) {
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

        splash.setProgress(10, "Initialize logging system");
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
        Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
        
        if (prefs.get("guiLang", null) != null) {
            Locale localeEnum = Locale.valueOf(prefs.get("guiLang", null));
            java.lang.System.setProperty("user.language", localeEnum.getLocale().getLanguage());
            java.lang.System.setProperty("user.country",  localeEnum.getLocale().getCountry());
            Logger.getLogger().debug("Set interface locale: {0}", Language.getLocale());
        }
    }

}
