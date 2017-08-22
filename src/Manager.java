
import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.update.UpdateUnit;
import codex.utils.ImageUtils;
import nodes.CommonRoot;
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
    
    public Manager() {
        window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));
        window.addUnit(new LogUnit(), window.loggingPanel);
        
        CommonRoot      root = new CommonRoot();
        RepositoryRoot repos = new RepositoryRoot();
        DatabaseRoot   bases = new DatabaseRoot();
        SystemRoot   systems = new SystemRoot();
        root.insert(repos);
        root.insert(bases);
        root.insert(systems);
        NodeTreeModel treeModel = new NodeTreeModel(root);
//        treeModel.setMode(repos,   Node.MODE_NONE);
//        treeModel.setMode(bases,   Node.MODE_NONE);
//        treeModel.setMode(systems, Node.MODE_NONE);
        window.addUnit(new ExplorerUnit(treeModel), window.explorePanel);
        
        window.addUnit(new UpdateUnit(), window.upgradePanel);
        window.setVisible(true);
    }
    
}
