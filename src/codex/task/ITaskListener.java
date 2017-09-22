package codex.task;

public interface ITaskListener {
    
    public void statusChanged(Status status);
    public void progressChanged(int percent, String description);
    
}
