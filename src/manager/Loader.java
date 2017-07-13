package manager;

import codex.log.Logger;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.SimpleLayout;


public class Loader {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        Logger logger = Logger.getLogger();
        logger.addAppender(new ConsoleAppender(new SimpleLayout(), "System.out"));
        //logger.addAppender(new ConsoleAppender(new PatternLayout("%d{ABSOLUTE} [%5p] %m%n"), "System.out"));

        logger.debug("DEBUG");
//        logger.info("INFO");
//        logger.warn("WARN");
//        logger.error("ERROR");
//        logger.fatal("FATAL");
//       
//        logger.fatal("Error:", new Error("Exception"));
        
        //logger.info("Entity ID={0}, Title={1}", new Object[] {4, "Demo"});
        
    }
    
}
