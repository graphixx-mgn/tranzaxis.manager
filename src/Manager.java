
import codex.explorer.ExplorerUnit;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.utils.ImageUtils;
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
        window.addUnit(new ExplorerUnit(), window.explorePanel);
        
        window.setVisible(true);
    }
    
}
