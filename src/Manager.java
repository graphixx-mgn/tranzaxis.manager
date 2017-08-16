
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
        //window.addUnit(new UpdateUnit(), window.upgradePanel);
        
        window.setVisible(true);
        logger.debug("TESD");
        logger.info("TESD");
        logger.warn("TESD");
        logger.error("Some error", new Error("This is an exception"));
        logger.info("TESD");
    }
    
}
