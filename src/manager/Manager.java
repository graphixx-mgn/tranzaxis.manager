package manager;


import codex.config.ConfigStoreService;
import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import codex.update.UpdateUnit;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import manager.nodes.Common;
import manager.nodes.DatabaseRoot;
import manager.nodes.RepositoryRoot;
import manager.nodes.SystemRoot;
import manager.type.Locale;
import manager.ui.Splash;
import org.apache.log4j.Level;
import manager.ui.Window;

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
    private final TaskManager  taskManager;
    private final AbstractUnit logUnit, updateUnit, explorerUnit;
    
    private class TaskImpl extends AbstractTask<Integer> {

        private Integer val; 
        
        public TaskImpl(Integer initVal) {
            super("Value incrementator starts from "+(initVal*5));
            val = initVal*5;
        }

        @Override
        public Integer execute() throws Exception {
            if (val == 0) throw new Error("Some execution exception (for test)");
            Thread.sleep(300);
            for (int i = 0; i < 10; i++) {
                Thread.sleep(500);
                val = val + 1;
                setProgress((i+1)*10, "Changed value to "+val);
            }
            return val;
        }
        
        @Override
        public void finished(Integer result) {
            Logger.getLogger().info("Result: {0}", result);
        }
    
    }
    
    public Manager() {
        loadSystemProps();
        Splash splash = new Splash();
        splash.setVisible(true);
        
        logUnit = new LogUnit();
        
        splash.setProgress(20, "Read configuration");
        ServiceRegistry.getInstance().registerService(new ConfigStoreService(new File(CONFIG_PATH)));
        
        splash.setProgress(30, "Start task management system");
        taskManager = new TaskManager();

        splash.setProgress(40, "Build explorer tree");
        Common          root = new Common();
        RepositoryRoot repos = new RepositoryRoot();
        DatabaseRoot   bases = new DatabaseRoot();
        SystemRoot   systems = new SystemRoot();

        root.insert(repos);
        root.insert(bases);
        root.insert(systems);
        
        NodeTreeModel treeModel = new NodeTreeModel(root);
        explorerUnit = new ExplorerUnit(treeModel);
        
        splash.setProgress(70, "Start upgrade management system");
        updateUnit   = new UpdateUnit();
        
        ((JButton) updateUnit.getViewport()).addActionListener((ActionEvent e) -> {
            ITask task;
            for (int cnt = 1; cnt < 4; cnt++) {
                task = new TaskImpl(cnt);
                ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).executeTask(task);
            }
            ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).enqueueTask(
                    new GroupTask("Some compound task", new TaskImpl(150), new TaskImpl(200), new TaskImpl(100))
            );
        });

        splash.setProgress(80, "Initialization user interface");
        window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        window.addUnit(logUnit, window.loggingPanel);
        window.addUnit(updateUnit, window.upgradePanel);
        window.addUnit(explorerUnit, window.explorePanel);
        window.addUnit(taskManager, window.taskmgrPanel);
        splash.setProgress(100);
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
