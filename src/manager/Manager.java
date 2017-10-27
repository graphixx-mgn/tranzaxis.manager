package manager;


import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.task.AbstractTask;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import codex.update.UpdateUnit;
import codex.utils.Formatter;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import manager.nodes.CommonRoot;
import manager.nodes.Database;
import manager.nodes.System;
import manager.nodes.DatabaseRoot;
import manager.nodes.Development;
import manager.nodes.ReleaseList;
import manager.nodes.Repository;
import manager.nodes.RepositoryRoot;
import manager.nodes.SystemRoot;
import org.apache.log4j.Level;
import manager.ui.Window;

public class Manager {
    
    private final static Logger logger = Logger.getLogger();
    static {
        logger.setLevel(Level.ALL);
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
        logUnit = new LogUnit();
        taskManager = new TaskManager();

        CommonRoot      root = new CommonRoot();
        RepositoryRoot repos = new RepositoryRoot();
        DatabaseRoot   bases = new DatabaseRoot();
        SystemRoot   systems = new SystemRoot();
        
        Repository      repo = new Repository("TWRBS");
        Database        base = new Database("Virtual");
        System        system = new System("Virtual");
        
        Development      dev = new Development();
        ReleaseList releases = new ReleaseList();
        
        root.insert(repos);
        root.insert(bases);
        root.insert(systems);
        
        repos.insert(repo);
        bases.insert(base);
        systems.insert(system);
        
        repo.insert(dev);
        repo.insert(releases);
        
        NodeTreeModel treeModel = new NodeTreeModel(root);
        
        updateUnit   = new UpdateUnit();
        explorerUnit = new ExplorerUnit(treeModel);
        
        ((JButton) updateUnit.getViewport()).addActionListener((ActionEvent e) -> {
            ITask task;
            for (int cnt = 1; cnt < 4; cnt++) {
                task = new TaskImpl(cnt);
                taskManager.execute(task);
            }
            taskManager.enqueue(new GroupTask("Some compound task", new TaskImpl(150), new TaskImpl(200), new TaskImpl(100)));
        });

        
        window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        window.addUnit(logUnit, window.loggingPanel);
        window.addUnit(updateUnit, window.upgradePanel);
        window.addUnit(explorerUnit, window.explorePanel);
        window.addUnit(taskManager, window.taskmgrPanel);
        
        Runtime runtime = Runtime.getRuntime();
        StringBuilder sb = new StringBuilder();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        
        sb.append("free memory: ").append(Formatter.formatSize(freeMemory / 1024)).append("\n");
        sb.append("allocated memory: ").append(Formatter.formatSize(allocatedMemory / 1024)).append("\n");
        sb.append("max memory: ").append(Formatter.formatSize(maxMemory / 1024)).append("\n");
        sb.append("total free memory: ").append(Formatter.formatSize((freeMemory + (maxMemory - allocatedMemory)) / 1024)).append("\n");
        java.lang.System.err.println(sb);
        
        window.setVisible(true);
    }
    
}
