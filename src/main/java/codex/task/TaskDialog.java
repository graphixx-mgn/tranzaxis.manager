package codex.task;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Диалог отображения исполнения задач. Окно, содержащее виджеты исполняющихся 
 * в данный момент задач, запущенных методом {@link TaskManager.TaskExecutorService#executeTask(codex.task.ITask)}.
 */
class TaskDialog extends Dialog implements ITaskListener {
    
    /**
     * Код выхода при нажатии кнопки перемещения задач в очередь.
     */
    public static final int           ENQUEUE    = 100;
    /**
     * Код выхода при нажатии кнопки отмены всех задач.
     */
    public static  final int          CANCEL     = 1;
    
    private static final DialogButton BTN_QUEUE  = new DialogButton(
            ImageUtils.resize(ImageUtils.getByPath("/images/enqueue.png"), 22, 22), Language.get("enqueue@title"), -1, ENQUEUE
    );
    private static final DialogButton BTN_CANCEL = new DialogButton(
            ImageUtils.resize(ImageUtils.getByPath("/images/cancel.png"), 22, 22), Language.get("cancel@title"), -1, CANCEL
    );

    private final JPanel viewPanel;
    final Map<ITask, AbstractTaskView> taskRegistry = new ConcurrentHashMap<>();
    private final ExecutorService dialogThread = Executors.newCachedThreadPool();

    /**
     * Конструктор окна.
     * @param closeAction Слушатель события закрытия окна.
     */
    TaskDialog(Window parent, ActionListener closeAction) {
        super(parent, 
                ImageUtils.getByPath("/images/progress.png"),
                Language.get("title"),
                new JPanel(),
                closeAction,
                BTN_QUEUE, BTN_CANCEL
        );

        JPanel viewPort = new JPanel();
        viewPort.setLayout(new BorderLayout());
        
        viewPanel = new JPanel();
        viewPanel.setLayout(new BoxLayout(viewPanel, BoxLayout.Y_AXIS));
        viewPanel.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY)
        ));
        viewPort.add(viewPanel, BorderLayout.NORTH);
        setContent(viewPort);
    }
    
    /**
     * Регистрация новой задачи.
     */
    void addTask(ITask task) {
        task.addListener(this);
        taskRegistry.put(task, task.createView(new Consumer<ITask>() {
            @Override
            public void accept(ITask context) {
                if (context.getStatus() == Status.PENDING || context.getStatus() == Status.STARTED) {
                    context.cancel(true);
                }
            }
        }));

        dialogThread.submit(
            () -> {
                viewPanel.add(taskRegistry.get(task));
                setVisible(true);
            }
        );
    }

    void removeTask(ITask task) {
        AbstractTaskView view = taskRegistry.remove(task);
        dialogThread.submit(() -> {
            viewPanel.remove(view);
            viewPanel.revalidate();
            viewPanel.repaint();
            setSize(new Dimension(getSize().width, getPreferredSize().height));
        });
    }
    
    /**
     * Очистка окна.
     */
    void clearTasks() {
        dialogThread.submit(
            () -> {
                setVisible(false);
                taskRegistry.clear();
                viewPanel.removeAll();
            }
        );
    }

    long runningTasks() {
        return taskRegistry.keySet().stream().filter(queued -> !queued.getStatus().isFinal()).count();
    }

    long failedTasks() {
        return taskRegistry.keySet().stream().filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED).count();
    }

    boolean isReady() {
        return runningTasks() + failedTasks() == 0;
    }

    @Override
    public void statusChanged(ITask task, Status status) {
        if (status == Status.CANCELLED) {
            removeTask(task);
        }
        BTN_QUEUE.setEnabled(runningTasks() != 0);
        if (isReady()) {
            clearTasks();
        }
    }
    
}
