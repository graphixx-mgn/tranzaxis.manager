package codex.task;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Диалог отображения исполнения задач. Окно, содержащее виджеты исполняющихся 
 * в данный момент задач, запущенных методом {@link TaskManager.TaskExecutorService#executeTask(codex.task.ITask)}.
 */
class TaskDialog extends Dialog implements ITaskListener {
    
    /**
     * Код выхода при нажатии кнопки перемещения задач в очередь.
     */
    static final int  ENQUEUE = 100;
    /**
     * Код выхода при нажатии кнопки отмены всех задач.
     */
    static  final int CANCEL  = 1;
    
    private static final DialogButton BTN_QUEUE  = new DialogButton(
            ImageUtils.resize(ImageUtils.getByPath("/images/enqueue.png"), 22, 22), Language.get("enqueue@title"), -1, ENQUEUE
    );
    private static final DialogButton BTN_CANCEL = new DialogButton(
            ImageUtils.resize(ImageUtils.getByPath("/images/cancel.png"), 22, 22), Language.get("cancel@title"), -1, CANCEL
    );

    private static final int MIN_WIDTH = 600;

    private final JPanel viewPanel;
    final Map<ITask, AbstractTaskView> taskRegistry = new ConcurrentHashMap<>();

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
        setResizable(false);

        JPanel viewPort = new JPanel();
        viewPort.setLayout(new BorderLayout());
        
        viewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setClip(getVisibleRect());
                super.paintComponent(g);
            }
        };
        viewPanel.setLayout(new BoxLayout(viewPanel, BoxLayout.Y_AXIS));
        viewPanel.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY)
        ));
        viewPort.add(viewPanel, BorderLayout.NORTH);
        setContent(viewPort);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        return new Dimension(Math.max(preferred.width, MIN_WIDTH), preferred.height);
    }

    /**
     * Регистрация новой задачи.
     */
    void addTask(ITask task) {
        task.addListener(this);
        AbstractTaskView view = task.createView(new Consumer<ITask>() {
            @Override
            public void accept(ITask task) {
                if (task.getStatus() == Status.PENDING || task.getStatus() == Status.STARTED) {
                    task.cancel(true);
                } else {
                    removeTask(task);
                }
            }
        });
        taskRegistry.put(task, view);
        viewPanel.add(view);

        if (!isVisible()) {
            new Thread(() -> {
                setVisible(true);
            }).start();
        } else {
            pack();
        }
    }

    void removeTask(ITask task) {
        task.removeListener(this);
        AbstractTaskView view = taskRegistry.remove(task);
        viewPanel.remove(view);
        pack();
    }
    
    /**
     * Очистка окна.
     */
    void clearTasks() {
        for (ITask task : taskRegistry.keySet()) {
            task.removeListener(this);
        }
        taskRegistry.clear();
        viewPanel.removeAll();
        new Thread(() -> {
            setVisible(false);
        }).start();
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
        if (status == Status.CANCELLED|| status == Status.FINISHED) {
            removeTask(task);
        }
        BTN_QUEUE.setEnabled(runningTasks() != 0);
        if (isReady()) {
            clearTasks();
        }
    }

    @Override
    public void setVisible(boolean visible) {
        try {
            super.setVisible(visible);
        } catch (Throwable e) {
            //
        }
    }
    
}
