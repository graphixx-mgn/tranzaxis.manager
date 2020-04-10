package codex.scheduler;

import codex.model.CommandRegistry;
import codex.service.ServiceRegistry;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.type.EntityRef;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractJob extends Job {

    static {
        CommandRegistry.getInstance().registerCommand(ExecuteJob.class);
    }

    public AbstractJob(EntityRef owner, String title) {
        super(owner, title);
    }

    protected abstract Collection<ITask> getTasks();

    public final void executeJob(ITaskListener listener, boolean foreground) {
        Collection<ITask> task = getTasks();
        ITask scheduleTask = new GroupTask(
                MessageFormat.format(Language.get(Job.class, "task@title"), getTitle()),
                false,
                task.toArray(new ITask[]{})
        );

        if (listener != null) {
            scheduleTask.addListener(listener);
        }

        new Thread(() -> {
            try {
                getLock().acquire();
                if (foreground) {
                    ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).executeTask(scheduleTask);
                } else {
                    ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).enqueueTask(scheduleTask);
                }
                scheduleTask.get();
            } catch (InterruptedException | ExecutionException | CancellationException ignore) {
                //
            } finally {
                getLock().release();
                switch (scheduleTask.getStatus()) {
                    case FAILED:    setJobStatus(JobScheduler.JobStatus.Failed);   break;
                    case FINISHED:  setJobStatus(JobScheduler.JobStatus.Finished); break;
                    case CANCELLED: setJobStatus(JobScheduler.JobStatus.Canceled); break;
                }
            }
        }).start();
    }
}
