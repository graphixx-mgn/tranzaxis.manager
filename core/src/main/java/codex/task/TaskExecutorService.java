package codex.task;

import codex.context.IContext;
import codex.log.Logger;
import codex.notification.NotifySource;
import codex.notification.INotificationService;
import codex.notification.Message;
import codex.notification.TrayInformer;
import codex.service.*;
import codex.utils.Language;
import java.awt.*;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Сервис, принимающий задачи на исполнение.
 */
@NotifySource
@IContext.Definition(id = "TES", name = "Task Executor Service", icon = "/images/tasks.png")
public class TaskExecutorService extends AbstractService<TaskServiceOptions> implements ITaskExecutorService, IContext, ITaskListener {

    private final static INotificationService NSS = ServiceRegistry.getInstance().lookupService(INotificationService.class);

    private final Map<ThreadPoolKind, ITaskMonitor> monitors = new HashMap<>();
    private final ITaskMonitor defMonitor = new DefaultMonitor();
    private final ITaskListener notifyListener = new ITaskListener() {
        @Override
        public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
            if (nextStatus == Status.FAILED || nextStatus == Status.FINISHED) {
                NSS.sendMessage(TrayInformer.getInstance(), new Message(
                        task.getStatus() == Status.FINISHED ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.ERROR,
                        Language.get(TaskMonitor.class,"notify@"+task.getStatus().name().toLowerCase()),
                        task.getTitle()
                ));
                task.removeListener(this);
            }
        }
    };

    @Override
    public void startService() {
        super.startService();
        attachMonitor(null, defMonitor); //Default monitor
        attachMonitor(ThreadPoolKind.Demand, defMonitor);
    }

    @Override
    public Accessor getAccessor() {
        return new Accessor() {
            @Override
            void attachMonitor(ThreadPoolKind kind, ITaskMonitor monitor) {
                TaskExecutorService.this.attachMonitor(kind, monitor);
            }
        };
    }

    @Override
    public void enqueueTask(ITask task) {
        execute(ThreadPoolKind.Queued, task,false);
    }

    @Override
    public void executeTask(ITask task) {
        execute(ThreadPoolKind.Demand, task,false);
    }

    @Override
    public void quietTask(ITask task) {
        execute(ThreadPoolKind.Demand, task,true);
    }

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        Logger.getLogger().debug(
                "Task ''{0}'' state changed: {1} -> {2}{3}",
                task.getTitle(),
                prevStatus,
                nextStatus,
                nextStatus.isFinal() ?
                        MessageFormat.format(" (duration: {0})", TaskView.formatDuration(((AbstractTask) task).getDuration())) :
                        ""
        );
        if (nextStatus.isFinal()) {
            task.removeListener(this);
        }
    }

    private void attachMonitor(ThreadPoolKind kind, ITaskMonitor monitor) {
        synchronized (monitors) {
            monitors.putIfAbsent(kind, monitor);
        }
        if (kind == ThreadPoolKind.Queued) {
            defMonitor.setTaskRecipient(monitor);
        }
    }

    private ITaskMonitor getMonitor(ThreadPoolKind kind) {
        return monitors.computeIfAbsent(
                kind,
                defaultMonitor -> monitors.computeIfAbsent(
                        null,
                        emptyMonitor -> new ITaskMonitor.MonitorAdapter()
                )
        );
    }

    private void execute(ThreadPoolKind kind, ITask task, boolean quiet) {
        if (!quiet) {
            ITaskMonitor monitor = getMonitor(kind);
            monitor.registerTask(task);
            if (kind == ThreadPoolKind.Queued && monitor != defMonitor) {
                task.addListener(notifyListener);
            }
        }
        task.addListener(this);
        Callable<Object> r = () -> execute(task);

        kind.getExecutor().submit(r);
    }

    private static Object execute(ITask task) throws ExecutionException, InterruptedException {
        task.run();
        return task.get();
    }
}