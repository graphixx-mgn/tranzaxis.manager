package codex.task;

public interface ITaskListener {
    
    public void statusChanged(ITask task, Status status);
    public void progressChanged(ITask task,  int percent, String description);
    
}
