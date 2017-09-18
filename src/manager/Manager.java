package manager;


import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.service.DemoService;
import codex.service.DummyService;
import codex.service.ServiceRegistry;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import codex.update.UpdateUnit;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import manager.nodes.CommonRoot;
import manager.nodes.System;
import manager.nodes.DatabaseRoot;
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
    private final AbstractUnit logUnit, updateUnit, explorerUnit;
    
    public Manager() {
        logUnit = new LogUnit();
        
        TaskManager manager = new TaskManager();
        manager.test();
        
//        ServiceRegistry.getInstance().registerService(new DemoService());
//        ServiceRegistry.getInstance().processRequest(DummyService.class);
        
//        JDialog p = new JDialog();
//        double version = Double.parseDouble(java.lang.System.getProperty("java.specification.version"));
//        p.add(new JLabel("Version: "+version, ImageUtils.getByPath("/images/project.png"), SwingConstants.LEFT));
//        p.setModal(true);
//        p.pack();
//        p.setLocationRelativeTo(null);
//        p.setVisible(true);

        CommonRoot      root = new CommonRoot();
        RepositoryRoot repos = new RepositoryRoot();
        DatabaseRoot   bases = new DatabaseRoot();
        SystemRoot   systems = new SystemRoot();
        System        system = new System();
        systems.insert(system);
        root.insert(repos);
        root.insert(bases);
        root.insert(systems);
        NodeTreeModel treeModel = new NodeTreeModel(root);
        
        updateUnit   = new UpdateUnit();
        explorerUnit = new ExplorerUnit(treeModel);

        window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        window.addUnit(logUnit, window.loggingPanel);
        window.addUnit(updateUnit, window.upgradePanel);
        window.addUnit(explorerUnit, window.explorePanel);
        window.setVisible(true);
    }
    
}
