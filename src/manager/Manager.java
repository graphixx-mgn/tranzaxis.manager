package manager;

import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.InstanceUnit;
import codex.launcher.LauncherUnit;
import codex.log.LogUnit;
import codex.service.ServiceUnit;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import manager.nodes.Common;
import manager.nodes.DatabaseRoot;
import manager.nodes.EnvironmentRoot;
import manager.nodes.RepositoryRoot;
import manager.type.Locale;
import manager.ui.splash.SplashScreen;
import manager.ui.Window;
import manager.upgrade.UpgradeUnit;
import sun.util.logging.PlatformLogger;

public class Manager {
    
    static {
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
            UIManager.put("Tree.drawDashedFocusIndicator", false);
            
        } catch (UnsupportedLookAndFeelException e) {}
    }
    private final AbstractUnit 
        logViewer, 
        upgradeUnit, 
        configExplorer, 
        commandLauncher, 
        serviceOptions,
        networkBrowser,
        taskManager
    ;

    public Manager() {
        SplashScreen splash = new SplashScreen("images/splash.png");
        
        loadSystemProps();
        PlatformLogger platformLogger = PlatformLogger.getLogger("java.util.prefs");
        platformLogger.setLevel(PlatformLogger.Level.OFF);
        
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
        logViewer = LogUnit.getInstance();

        splash.setProgress(40, "Start task management system");
        taskManager = new TaskManager();

        splash.setProgress(50, "Build configuration root");
        Common root = new Common();
        root.insert(new RepositoryRoot());
        root.insert(new DatabaseRoot());
        root.insert(new EnvironmentRoot());
        
        NodeTreeModel objectsTree = new NodeTreeModel(root);
        configExplorer = ExplorerUnit.getInstance();
        ((ExplorerUnit) configExplorer).setModel(objectsTree);
        
        splash.setProgress(70, "Start command launcher unit");
        commandLauncher = new LauncherUnit();
//        
        splash.setProgress(80, "Start service management unit");
        serviceOptions = new ServiceUnit();
        
        splash.setProgress(90, "Start instance control unit");
        networkBrowser = InstanceUnit.getInstance();

        splash.setProgress(90, "Start upgrade unit");
        upgradeUnit = new UpgradeUnit();

        splash.setProgress(100, "Initialize user interface");
        window.addUnit(logViewer,       window.loggingPanel);
        window.addUnit(serviceOptions,  window.servicePanel);
        window.addUnit(upgradeUnit,     window.upgradePanel);
        window.addUnit(configExplorer,  window.explorePanel);
        window.addUnit(commandLauncher, window.launchPanel);
        window.addUnit(networkBrowser,  window.connectPanel);
        window.addUnit(taskManager,     window.taskmgrPanel);

        splash.setVisible(false);
        window.setVisible(true);
    }
    
    private void loadSystemProps() {
        Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
        
        if (prefs.get("guiLang", null) != null) {
            Locale localeEnum = Locale.valueOf(prefs.get("guiLang", null));
            java.lang.System.setProperty("user.language", localeEnum.getLocale().getLanguage());
            java.lang.System.setProperty("user.country",  localeEnum.getLocale().getCountry());
        }
    }

}
