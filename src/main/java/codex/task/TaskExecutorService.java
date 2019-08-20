package codex.task;

import codex.context.IContext;
import codex.log.Level;
import codex.log.Logger;
import codex.notification.NotifySource;
import codex.notification.INotificationService;
import codex.notification.Message;
import codex.notification.TrayInformer;
import codex.service.*;
import codex.utils.Language;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис, принимающий задачи на исполнение.
 */
@NotifySource
@IContext.Definition(id = "TES", name = "Task Executor Service", icon = "/images/tasks.png")
public class TaskExecutorService extends AbstractService<TaskServiceOptions> implements ITaskExecutorService, IContext {

    private final static INotificationService NSS = ServiceRegistry.getInstance().lookupService(INotificationService.class);

    private final Map<ThreadPoolKind, ITaskMonitor> monitors = new HashMap<>();
    private final TaskDialog taskDialog = new TaskDialog(null);
    private final ITaskListener notifyListener = new ITaskListener() {
        @Override
        public void statusChanged(ITask task, Status status) {
            if (status == Status.FAILED || status == Status.FINISHED) {
                NSS.sendMessage(TrayInformer.getInstance(), new Message(
                        task.getStatus() == Status.FINISHED ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.ERROR,
                        Language.get(TaskMonitor.class,"notify@"+task.getStatus().name().toLowerCase()),
                        task.getTitle()
                ));
                task.removeListener(this);
            }
        }
    };

    static void logEvent(Level level, String message) {
        Logger.getLogger().log(level, message);
    }

    @Override
    public void startService() {
        super.startService();
        attachMonitor(null, taskDialog); //Default monitor
        attachMonitor(ThreadPoolKind.Demand, taskDialog);
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

    private void attachMonitor(ThreadPoolKind kind, ITaskMonitor monitor) {
        synchronized (monitors) {
            monitors.putIfAbsent(kind, monitor);
        }
        if (kind == ThreadPoolKind.Queued) {
            taskDialog.setTaskRecipient(monitor);
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
            task.addListener(monitor);
            if (kind == ThreadPoolKind.Queued && monitor != taskDialog) {
                task.addListener(notifyListener);
            }
        }
        kind.getExecutor().submit(task);
    }

}
