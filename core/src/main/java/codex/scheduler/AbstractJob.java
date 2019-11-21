package codex.scheduler;

import codex.model.CommandRegistry;
import codex.service.ServiceRegistry;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.type.EntityRef;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractJob extends Job {

    static {
        CommandRegistry.getInstance().registerCommand(ExecuteJob.class);
    }

    public AbstractJob(EntityRef owner, String title) {
        super(owner, title);
    }

    protected abstract ITask getTask();

    protected void executeJob(ITaskListener listener, boolean foreground) {
        ITask task = getTask();

        if (listener != null) {
            task.addListener(listener);
        }

        new Thread(() -> {
            try {
                getLock().acquire();
                if (foreground) {
                    ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).executeTask(task);
                } else {
                    ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).enqueueTask(task);
                }
                task.get();
            } catch (InterruptedException | ExecutionException | CancellationException ignore) {
                //
            } finally {
                getLock().release();
                switch (task.getStatus()) {
                    case FAILED:    setJobStatus(JobScheduler.JobStatus.Failed);   break;
                    case FINISHED:  setJobStatus(JobScheduler.JobStatus.Finished); break;
                    case CANCELLED: setJobStatus(JobScheduler.JobStatus.Canceled); break;
                }
            }
        }).start();
    }
}
