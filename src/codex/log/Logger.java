package codex.log;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class Logger implements Runnable {
    
    private static Logger instance;
    
    private final BlockingQueue<LogRecord> inputPipe = new LinkedBlockingQueue();
    private final List<BlockingQueue<LogRecord>> outputPipes = new CopyOnWriteArrayList<>();
    
    private Logger() {
        outputPipes.add(new LinkedBlockingQueue<>());
        Thread logReader = new Thread(this, "Codex LogDispatcher");
        logReader.setDaemon(true);
        logReader.start();
    };
    
    public static Logger getLogger() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }
    
    public final void log(Level level, String message) {
        put(new LogRecord(level, message));
    }
    
    public final void log(Level level, String message, Object[] params) {
        LogRecord record = new LogRecord(level, message);
        record.setParameters(params);
        put(record);
    }
    
    public final void addConsumer() {
        BlockingQueue<LogRecord> newPipe = new LinkedBlockingQueue();
        outputPipes.add(newPipe);
    }
    
    @Override
    public void run() {
        while (true) {
            try {
                LogRecord record = inputPipe.take();
                for(BlockingQueue<LogRecord> queue : outputPipes){
                    queue.put(record);
                    System.out.println(record.getMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void put(LogRecord record) {
        try {
            inputPipe.put(record);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
