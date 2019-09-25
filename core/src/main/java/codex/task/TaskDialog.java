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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Диалог отображения исполнения задач. Окно, содержащее виджеты исполняющихся 
 * в данный момент задач, запущенных методом {@link TaskExecutorService#executeTask(codex.task.ITask)}.
 */
class TaskDialog extends Dialog implements ITaskMonitor {

    private static final int MIN_WIDTH = 600;

    private final Map<ITask, AbstractTaskView> taskViews = new HashMap<>();
    private final JPanel taskViewList;

    private final Container    buttonPanel;
    private final DialogButton buttonEnqueue;

    /**
     * Конструктор окна.
     * //@param closeAction Слушатель события закрытия окна.
     */
    TaskDialog(Window parent) {
        super(
                parent,
                ImageUtils.getByPath("/images/progress.png"),
                Language.get("title"),
                new JPanel(),
                null,
                Default.BTN_OK.newInstance(
                        ImageUtils.getByPath("/images/enqueue.png"),
                        Language.get("enqueue@title")
                ),
                Default.BTN_CANCEL.newInstance()
        );
        setResizable(false);

        handler = (button) -> (keyEvent) -> {
            setVisible(false);
            int ID = button == null ? EXIT : button.getID();
            if (ID == Dialog.CANCEL || ID == Dialog.EXIT) {
                taskViews.keySet().forEach((task) -> task.cancel(true));
                clearRegistry();
            }
            if (ID == Dialog.OK) {
                new HashSet<>(taskViews.keySet()).forEach(task -> {
                    unregisterTask(task);
                    if (!task.getStatus().isFinal()) {
                        taskRecipient.registerTask(task);
                        task.addListener(taskRecipient);
                    }
                });
            }
        };

        JPanel viewPort = new JPanel();
        viewPort.setLayout(new BorderLayout());

        taskViewList = new JPanel();
        taskViewList.setLayout(new BoxLayout(taskViewList, BoxLayout.Y_AXIS));
        taskViewList.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY)
        ));
        viewPort.add(taskViewList, BorderLayout.NORTH);
        setContent(viewPort);

        buttonEnqueue = getButton(Dialog.OK);
        buttonPanel = buttonEnqueue.getParent();
        buttonPanel.remove(buttonEnqueue);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        return new Dimension(Math.max(preferred.width, MIN_WIDTH), preferred.height);
    }

    @Override
    public void beforeExecute(ITask task) {
        registerTask(task);
    }

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        Collection<ITask> tasks = new HashSet<>(taskViews.keySet());
        long running  = tasks.stream().filter(queued -> !queued.getStatus().isFinal()).count();
        long stopped  = tasks.stream().filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED).count();
        boolean ready = running + stopped == 0;
        if (!tasks.isEmpty() && ready) {
            clearRegistry();
        }
    }

    @Override
    public void registerTask(ITask task) {
        taskViews.put(task, task.createView(new Consumer<ITask>() {
            @Override
            public void accept(ITask context) {
                context.cancel(true);
                unregisterTask(context);
                statusChanged(context, context.getStatus(), context.getStatus());
            }
        }));
        taskViewList.add(taskViews.get(task));

        if (!isVisible()) {
            new Thread(() -> setVisible(true)).start();
        } else {
            pack();
        }
    }

    @Override
    public void unregisterTask(ITask task) {
        if (taskViews.containsKey(task)) {
            taskViewList.remove(taskViews.remove(task));
            task.removeListener(this);
            if (isVisible()) {
                repaint();
                if (taskViews.isEmpty()) {
                    new Thread(() -> setVisible(false)).start();
                } else {
                    pack();
                }
            }
        }
    }

    @Override
    public void clearRegistry() {
        new HashSet<>(taskViews.keySet()).forEach(this::unregisterTask);
        statusChanged(null, null, null);
    }

    private ITaskMonitor taskRecipient;
    @Override
    public void setTaskRecipient(ITaskMonitor monitor) {
        taskRecipient = monitor;
        if (taskRecipient != null) {
            buttonPanel.add(buttonEnqueue, 0);
        } else if (buttonEnqueue.getParent() != null) {
            buttonPanel.remove(buttonEnqueue);
        }
    }
    
}
