
import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.unit.AbstractUnit;
import codex.update.UpdateUnit;
import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import nodes.CommonRoot;
import nodes.System;
import nodes.DatabaseRoot;
import nodes.RepositoryRoot;
import nodes.SystemRoot;
import org.apache.log4j.Level;
import ui.Window;

public class Manager {
    
    private final static Logger logger = Logger.getLogger();
    static {
        logger.setLevel(Level.ALL);
    }
    
    private final Window window;
    private final AbstractUnit log, update, explorer;
    
    public Manager() {
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
            UIManager.put("Tree.drawDashedFocusIndicator", false);
        } catch (UnsupportedLookAndFeelException e) {}
        
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
        
        log      = new LogUnit();
        update   = new UpdateUnit();
        explorer = new ExplorerUnit(treeModel);

        window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        window.addUnit(log, window.loggingPanel);
        window.addUnit(update, window.upgradePanel);
        window.addUnit(explorer, window.explorePanel);
        window.setVisible(true);
    }
    
}
