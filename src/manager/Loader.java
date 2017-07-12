package manager;

import codex.log.Logger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class Loader {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        Logger logger = Logger.getLogger();
        logger.addHandler(new ConsoleHandler());
        logger.setLevel(Level.FINE);
        logger.log(Level.FINE, "Test {0}", new Object[] {4});
        logger.log(Level.SEVERE, new Error("Error"));
    }
    
}
