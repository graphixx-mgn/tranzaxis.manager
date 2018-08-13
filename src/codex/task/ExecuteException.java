package codex.task;

import java.util.concurrent.ExecutionException;

public class ExecuteException extends ExecutionException {
    
    private final String desc;

    public ExecuteException(String message, String desc) {
        super(message);
        this.desc = desc;
    }
    
    public String getDescription() {
        return desc;
    }
    
}
