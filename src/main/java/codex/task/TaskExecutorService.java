package codex.task;

import codex.notification.INotificationService;
import codex.notification.NotificationService;
import codex.notification.NotifyCondition;
import codex.service.AbstractService;
import codex.service.ServiceRegistry;
import codex.utils.Language;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис, принимающий задачи на исполнение.
 */
public class TaskExecutorService extends AbstractService<TaskServiceOptions> implements ITaskExecutorService {

    private final static String NS_SOURCE = "TaskManager/Task finished";

    private final Map<ThreadPoolKind, ITaskMonitor> monitors = new HashMap<>();
    private final TaskDialog taskDialog = new TaskDialog(null);
    private final ITaskListener notifyListener = new ITaskListener() {
        @Override
        public void statusChanged(ITask task, Status status) {
            if (status == Status.FAILED || status == Status.FINISHED) {
                String msgTitle = Language.get(TaskMonitor.class,"notify@"+task.getStatus().name().toLowerCase());
                ((INotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class)).showMessage(
                    NS_SOURCE,
                    msgTitle,
                    task.getTitle(),
                    task.getStatus() == Status.FINISHED ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.ERROR
                );
                task.removeListener(this);
            }
        }
    };

    @Override
    public void startService() {
        super.startService();
        attachMonitor(null, taskDialog); //Default monitor
        attachMonitor(ThreadPoolKind.Demand, taskDialog);

        ServiceRegistry.getInstance().addRegistryListener(INotificationService.class, (service) -> {
            ((NotificationService) service).registerSource(NS_SOURCE, NotifyCondition.INACTIVE);
        });
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

    void attachMonitor(ThreadPoolKind kind, ITaskMonitor monitor) {
        synchronized (monitors) {
            monitors.putIfAbsent(kind, monitor);
        }
        if (kind == ThreadPoolKind.Queued) {
            taskDialog.setTaskRecipient(monitor);
        }
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
