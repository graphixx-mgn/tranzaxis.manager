package codex.task;

import codex.component.panel.ScrollablePanel;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Монитор исполнения задач. Popup окно, появляющееся при нажатии на панель задач 
 * {@link TaskStatusBar} и содержащее виджеты исполняющихся в данный момент задач.
 * @see TaskView
 * @see GroupTaskView
 */
final class TaskMonitor extends JPopupMenu implements ITaskMonitor {

    private final List<ITaskMonitorListener>   listeners = new LinkedList<>();
    private final Map<ITask, AbstractTaskView> taskViews = new HashMap<>();

    private final ScrollablePanel taskViewList;

    /**
     * Конструктор монитора.
     * @param invoker Компонент GUI который вызывает отображение монитора. Необходим
     * для позиционирования окна.
     */
    TaskMonitor(JComponent invoker) {
        super();
        setInvoker(invoker);
        setBorder(new MatteBorder(1, 1, 0, 1, Color.GRAY));

        taskViewList = new ScrollablePanel();
        taskViewList.setLayout(new BoxLayout(taskViewList, BoxLayout.Y_AXIS));
        taskViewList.add(Box.createVerticalGlue());
        taskViewList.setScrollableWidth(ScrollablePanel.ScrollableSizeHint.FIT);

        JScrollPane taskScrollPane = new JScrollPane(taskViewList);
        taskScrollPane.getViewport().setBackground(Color.decode("#F5F5F5"));
        taskScrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(2, 2, 1, 2),
                new LineBorder(Color.LIGHT_GRAY, 1)
        ));
        taskScrollPane.setColumnHeader(null);
        add(taskScrollPane);
    }

    /**
     * Переключение состояния видимости окна.
     */
    @Override
    public void setVisible(boolean visibility) {
        if (visibility) {
            repaint();
            Point invokerLocation = getInvoker().getLocationOnScreen();
            setLocation(invokerLocation.x + 5, invokerLocation.y - getPreferredSize().height);
        }
        super.setVisible(visibility);
    }

    /**
     * Перерисовка окна и пересчет его позиции на экране.
     */
    @Override
    public void repaint() {
        if (getInvoker() != null) {
            setPreferredSize(new Dimension(
                getInvoker().getSize().width - 4, 400
            ));
            pack();
        }
        super.repaint();
    }

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        Collection<ITask> tasks = new HashSet<>(taskViews.keySet());
        long running  = tasks.stream().filter(queued -> !queued.getStatus().isFinal()).count();
        long stopped  = tasks.stream().filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED).count();
        long failed   = tasks.stream().filter(queued -> queued.getStatus() == Status.FAILED).count();
        boolean ready = running + stopped == 0;
        if (!tasks.isEmpty() && ready) {
            clearRegistry();
        }
        new LinkedList<>(listeners).forEach(listener -> listener.statusChanged(
                taskViews.size(), running, stopped, failed, getTotalProgress(tasks)
        ));
    }

    @Override
    public void progressChanged(ITask task, int percent, String description) {
        Collection<ITask> tasks = new HashSet<>(taskViews.keySet());
        long running = tasks.stream().filter(queued -> !queued.getStatus().isFinal()).count();
        long stopped = tasks.stream().filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED).count();
        long failed  = tasks.stream().filter(queued -> queued.getStatus() == Status.FAILED).count();
        new LinkedList<>(listeners).forEach(listener -> listener.statusChanged(
                taskViews.size(), running, stopped, failed, getTotalProgress(tasks)
        ));
    }

    @Override
    public void registerTask(ITask task) {
        task.addListener(this);
        taskViews.put(task, task.createView(new Consumer<ITask>() {
            @Override
            public void accept(ITask context) {
                if (!context.getStatus().isFinal()) {
                    context.cancel(true);
                } else {
                    unregisterTask(context);
                    statusChanged(context, context.getStatus(), context.getStatus());
                }
            }
        }));
        taskViewList.add(taskViews.get(task));
        statusChanged(task, task.getStatus(), task.getStatus());
    }

    @Override
    public void unregisterTask(ITask task) {
        task.removeListener(this);
        if (taskViews.containsKey(task)) {
            taskViewList.remove(taskViews.remove(task));
            task.removeListener(this);
            if (isVisible()) {
                repaint();
                setVisible(!taskViews.isEmpty());
            }
        }
    }

    @Override
    public void clearRegistry() {
        new HashSet<>(taskViews.keySet()).forEach(this::unregisterTask);
        statusChanged(null, null, null);
    }

    private int getTotalProgress(Collection<ITask> tasks) {
        return tasks.size() == 0 ? 0 : tasks.parallelStream()
                .mapToInt(task -> !task.getStatus().isFinal() ? task.getProgress() : 100)
                .sum() / tasks.size();
    }

    void addMonitorListener(ITaskMonitorListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    void removeMonitorListener(ITaskMonitorListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @FunctionalInterface
    interface ITaskMonitorListener {
        void statusChanged(int count, long running, long stopped, long failed, int progress);
    }
 
}
