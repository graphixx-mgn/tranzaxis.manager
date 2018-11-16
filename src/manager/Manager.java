package manager;

import codex.config.ConfigStoreService;
import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.launcher.LauncherUnit;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.service.ServiceUnit;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import manager.nodes.Common;
import manager.nodes.DatabaseRoot;
import manager.nodes.EnvironmentRoot;
import manager.nodes.RepositoryRoot;
import manager.type.Locale;
import manager.ui.Splash;
import manager.ui.Window;
import org.apache.log4j.Level;
import sun.util.logging.PlatformLogger;

public class Manager {
    
    static {
        Logger.getLogger().setLevel(Level.ALL);
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
            UIManager.put("Tree.drawDashedFocusIndicator", false);
        } catch (UnsupportedLookAndFeelException e) {}
    }
    
    private final static String CONFIG_PATH = java.lang.System.getProperty("user.home") + "/.manager.tranzaxis/manager.db";
    private static final Window WINDOW = new Window("TranzAxis Manager (v.2.0.4)", ImageUtils.getByPath("/images/project.png"));
    
    private final AbstractUnit 
            logUnit, 
            //updateUnit, 
            explorerUnit, 
            launchUnit, 
            serviceUnit,
            taskUnit
    ;

    public Manager() {
        PlatformLogger platformLogger = PlatformLogger.getLogger("java.util.prefs");
        platformLogger.setLevel(PlatformLogger.Level.OFF);
        
        String uniqueAppId = Manager.class.getCanonicalName();
        try {
            JUnique.acquireLock(uniqueAppId, (message) -> {
                WINDOW.setState(JFrame.NORMAL);
                WINDOW.setExtendedState(WINDOW.getExtendedState());
                WINDOW.toFront();
                WINDOW.requestFocus();
                return null;
            });
        } catch (AlreadyLockedException e) {
            JUnique.sendMessage(uniqueAppId, "OPEN");
            System.exit(0);
        }

        Splash splash = new Splash();
        splash.setVisible(true);
        
        logUnit = new LogUnit();
        Thread.setDefaultUncaughtExceptionHandler(Logger.getLogger());
                
        splash.setProgress(10, "Read configuration");
        ServiceRegistry.getInstance().registerService(new ConfigStoreService(new File(CONFIG_PATH)));
        loadSystemProps();
        
        splash.setProgress(30, "Start task management system");
        taskUnit = new TaskManager();

        splash.setProgress(50, "Build explorer tree");
        Common root = new Common();
        root.insert(new RepositoryRoot());
        root.insert(new DatabaseRoot());
        root.insert(new EnvironmentRoot());
        
        NodeTreeModel treeModel = new NodeTreeModel(root);
        explorerUnit = new ExplorerUnit(treeModel);
        
        splash.setProgress(70, "Start Launcher unit");
        launchUnit = new LauncherUnit();
        
        splash.setProgress(80, "Start service management system");
        serviceUnit = new ServiceUnit();
        
        
//        splash.setProgress(90, "Start upgrade management system");
        //updateUnit = new UpdateUnit();

//        ((JButton) updateUnit.getViewport()).addActionListener((ActionEvent e) -> {
//            MessageBox.show(MessageType.INFORMATION, "[ NOT SUPPORTED YET]");
//        });

        splash.setProgress(100, "Initialization user interface");
        WINDOW.addUnit(logUnit,      WINDOW.loggingPanel);
        //window.addUnit(updateUnit,   WINDOW.upgradePanel);
        WINDOW.addUnit(explorerUnit, WINDOW.explorePanel);
        WINDOW.addUnit(launchUnit,   WINDOW.launchPanel);
        WINDOW.addUnit(taskUnit,     WINDOW.taskmgrPanel);
        WINDOW.addUnit(serviceUnit,  WINDOW.servicePanel);
        
        splash.setProgress(100);
        splash.setVisible(false);        
        WINDOW.setVisible(true);
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
