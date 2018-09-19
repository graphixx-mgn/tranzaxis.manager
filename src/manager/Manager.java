package manager;


import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.launcher.LauncherUnit;
import codex.log.ILogMgmtService;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.task.TaskManager;
import codex.type.Enum;
import codex.unit.AbstractUnit;
import codex.update.UpdateUnit;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JButton;
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
    
    private final static String CONFIG_PATH = java.lang.System.getProperty("user.home") + "/.manager.tranzaxis/manager.db";
    
    static {
        Logger.getLogger().setLevel(Level.ALL);
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
            UIManager.put("Tree.drawDashedFocusIndicator", false);
        } catch (UnsupportedLookAndFeelException e) {}
    }
    
    private final Window window;
    private final AbstractUnit 
            logUnit, updateUnit, explorerUnit, 
            launchUnit, /*serviceUnit,*/ taskUnit;
    
    public Manager() {
        PlatformLogger platformLogger = PlatformLogger.getLogger("java.util.prefs");
        platformLogger.setLevel(PlatformLogger.Level.OFF);

        window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        Splash splash = new Splash();
        splash.setVisible(true);

        logUnit = new LogUnit();
                
        splash.setProgress(20, "Read configuration");
        loadSystemProps();
        ServiceRegistry.getInstance().registerService(new ConfigStoreService(new File(CONFIG_PATH)));
        
        splash.setProgress(30, "Start task management system");
        taskUnit = new TaskManager();

        splash.setProgress(50, "Build data tree model");
        Common root = new Common();
        
        NodeTreeModel treeModel = new NodeTreeModel(root);
        explorerUnit = new ExplorerUnit(treeModel);
        RepositoryRoot  repos = new RepositoryRoot(root.toRef());
        DatabaseRoot    bases = new DatabaseRoot(root.toRef());
        EnvironmentRoot envs  = new EnvironmentRoot(root.toRef());
        
        root.insert(repos);
        root.insert(bases);
        root.insert(envs);
        
        splash.setProgress(70, "Start Launcher unit");
        launchUnit = new LauncherUnit();
        
//        splash.setProgress(80, "Start service management system");
//        serviceUnit = new ServiceUnit();
        
        splash.setProgress(90, "Start upgrade management system");
        updateUnit = new UpdateUnit();

        ((JButton) updateUnit.getViewport()).addActionListener((ActionEvent e) -> {
            MessageBox.show(MessageType.INFORMATION, "[ NOT SUPPORTED YET]");
        });

        splash.setProgress(100, "Initialization user interface");
        window.addUnit(logUnit,      window.loggingPanel);
        window.addUnit(updateUnit,   window.upgradePanel);
        window.addUnit(explorerUnit, window.explorePanel);
        window.addUnit(launchUnit,   window.launchPanel);
        //window.addUnit(serviceUnit,  window.servicePanel);
        window.addUnit(taskUnit,     window.taskmgrPanel);
        splash.setProgress(100);
        splash.setVisible(false);        
        window.setVisible(true);
    }
    
    public void show() {
        if (window != null) {
            window.restoreState();
        }
    }
    
    private void loadSystemProps() {
        Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
        
        if (prefs.get("guiLang", null) != null) {
            Locale localeEnum = Locale.valueOf(prefs.get("guiLang", null));
            java.lang.System.setProperty("user.language", localeEnum.getLocale().getLanguage());
            java.lang.System.setProperty("user.country",  localeEnum.getLocale().getCountry());
        }
        if (prefs.get("logLevel", null) != null) {
            Enum minLevel = new Enum(codex.log.Level.Debug);
            minLevel.valueOf(prefs.get("logLevel", null));
            
            ILogMgmtService logMgmt = (ILogMgmtService) ServiceRegistry
                    .getInstance()
                    .lookupService(LogUnit.LogMgmtService.class);
            Map<codex.log.Level, Boolean> levelMap = new HashMap<>();
            EnumSet.allOf(codex.log.Level.class).forEach((level) -> {
                levelMap.put(level, level.ordinal() >= ((java.lang.Enum) minLevel.getValue()).ordinal());
            });
            logMgmt.changeLevels(levelMap);
        }
    }
    
}
