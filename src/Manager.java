
import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.utils.ImageUtils;
import node.CommonRoot;
import node.DatabaseRoot;
import node.RepositoryRoot;
import node.SystemRoot;
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
        
        CommonRoot root = new CommonRoot();
        RepositoryRoot repos = new RepositoryRoot();
        DatabaseRoot   bases = new DatabaseRoot();
        SystemRoot   systems = new SystemRoot();
        root.insert(repos);
        root.insert(bases);
        root.insert(systems);
        
        window.addUnit(new LogUnit(), window.loggingPanel);
        window.addUnit(new ExplorerUnit(new NodeTreeModel(root)), window.explorePanel);
        
        window.setVisible(true);
    }
    
}
