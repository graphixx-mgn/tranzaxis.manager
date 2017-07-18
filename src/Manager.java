
import codex.log.Logger;
import codex.model.AbstractModel;
import org.apache.log4j.Level;

public class Manager {
    
    private final static Logger logger = Logger.getLogger();
    static {
        logger.setLevel(Level.ALL);
    }
    
    public Manager() {
        logger.info("Manager started");
        
        AbstractModel model = new AbstractModel();
        
        
    }
    
}
