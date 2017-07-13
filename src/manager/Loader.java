package manager;

import codex.log.Logger;
import org.apache.log4j.Level;

public class Loader {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        Logger logger = Logger.getLogger();
        logger.setLevel(Level.ALL);

        Thread t1 = new Thread(new Runnable() {
            final Logger logger = Logger.getLogger();
            
            @Override
            public void run() {
                try {
                    int i = 0;
                    while (i < 10) {
                        logger.warn("Thread ''{0}'' posted: {1}", new Object[] {Thread.currentThread().getName(), i});
                        Thread.sleep(500);
                        i++;
                    }
                } catch (InterruptedException e) {
                    logger.warn("Error", e);
                }
            }
        });
        
        Thread t2 = new Thread(new Runnable() {
            final Logger logger = Logger.getLogger();

            @Override
            public void run() {
                try {
                    int i = 0;
                    while (i < 20) {
                        logger.debug("Thread ''{0}'' posted: {1}", new Object[] {Thread.currentThread().getName(), i});
                        Thread.sleep(200);
                        i++;
                    }
                } catch (InterruptedException e) {
                    logger.warn("Error", e);
                }
            }
        });
        
        logger.error("Start threads");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            logger.warn("Error", e);
        }

    }
    
}
