package codex.task;

import codex.notification.INotificationService;
import codex.notification.Message;
import codex.notification.TrayInformer;
import codex.service.*;
import codex.utils.Language;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Сервис, принимающий задачи на исполнение.
 */
@IContext.Definition(icon = "/images/tasks.png", title = "Task Executor Service")
public class TaskExecutorService extends AbstractService<TaskServiceOptions> implements ITaskExecutorService {

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

    @Override
    public void startService() {
        super.startService();
        attachMonitor(null, taskDialog); //Default monitor
        attachMonitor(ThreadPoolKind.Demand, taskDialog);

        ServiceRegistry.getInstance().addRegistryListener(INotificationService.class, (service) -> {
            ((INotificationService) service).registerSource(this);
        });
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
        final List<IContext> context = ServiceCallContext.getContext();
        Callable<Object> taskCallable = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                ServiceCallContext.clearContext();
                context.forEach(ServiceCallContext::enterContext);
                task.run();
                return task.get();
            }
        };
        kind.getExecutor().submit(taskCallable);
    }

}
