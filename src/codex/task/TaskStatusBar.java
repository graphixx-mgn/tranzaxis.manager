package codex.task;

import codex.component.ui.StripedProgressBarUI;
import codex.utils.Language;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Виджет модуля {@link TaskManager}, представляет собой панель задач с информацией о 
 * количестве исполняемых в данный момент задач, а также из статусы и общий прогресс.
 * Скрывается, если задач нет или все выполнены успешно. При нажатии вызывается 
 * окно просмотра задач {@link TaskMonitor}.
 */
final class TaskStatusBar extends JPanel implements ITaskListener {
    
    private final String PATTERN_NORMAL = Language.get("TaskStatus", "total@normal");
    private final String PATTERN_ERRORS = Language.get("TaskStatus", "total@errors");
    
    private final JLabel       status;
    private final JProgressBar progress;
    private final ClearButton  clear;
    private final List<ITask>  queue = new LinkedList<>();
    private final TaskMonitor  monitor;
    
    /**
     * Конструктор виджета.
     */
    TaskStatusBar(List<ExecutorService> threadPool) {
        super(new BorderLayout());
        setBorder(new EmptyBorder(2, 2, 2, 2));
        
        monitor = new TaskMonitor(this, threadPool, (task) -> {
            queue.remove(task);
            statusChanged(task, task.getStatus());
        });
        
        status = new JLabel();
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        status.setBorder(new EmptyBorder(0, 0, 0, 10));
        
        progress = new JProgressBar();
        progress.setMaximum(100);
        progress.setVisible(false);
        progress.setStringPainted(true);
        progress.setUI(new StripedProgressBarUI(true));
        progress.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        clear = new ClearButton();
        clear.setVisible(false);
        // Глобальный перехватчик событий
        // Иначе при первом клике закрывается монитор
        Toolkit.getDefaultToolkit().addAWTEventListener((AWTEvent event) -> {
            MouseEvent mouseEvent = (MouseEvent) event;
            if (event.getSource() == clear && mouseEvent.getID() == MouseEvent.MOUSE_CLICKED && clear.isEnabled()) {
                monitor.clearRegistry();
                queue.clear();
                statusChanged(null, null);
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(progress, BorderLayout.CENTER);
        controls.add(clear, BorderLayout.EAST);
        
        add(status, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                monitor.setVisible(!monitor.isVisible() && !queue.isEmpty());
            }
        });
    }
    
    /**
     * Регистрация новой задачи.
     */
    void addTask(ITask task) {
        queue.add(task);
        monitor.registerTask(task);
        task.addListener(this);
        statusChanged(task, task.getStatus());
    }

    @Override
    public void statusChanged(ITask task, Status newStatus) {
        long running  = queue.stream().filter(queued -> queued.getStatus() == Status.PENDING   || queued.getStatus() == Status.STARTED).count();
        long failed   = queue.stream().filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED).count();
        boolean ready = running + failed == 0;
        
        status.setVisible(!ready);
        progress.setVisible(!ready);
        clear.setVisible(!ready);
        clear.setEnabled(running == 0);
        
        if (ready) {
            progress.setValue(0);
            monitor.clearRegistry();
            queue.clear();
            return;
        }
        
        long finished = queue.stream().filter(queued -> queued.getStatus() == Status.FINISHED).count();
        status.setText(MessageFormat.format(failed > 0 ? PATTERN_ERRORS : PATTERN_NORMAL, running, finished, failed));
        if (task != null) {
            progressChanged(task, task.getProgress(), task.getDescription());
        }
    }

    @Override
    public void progressChanged(ITask task, int percent, String description) {
        progress.setValue(queue.stream().mapToInt(
                queued -> queued.getStatus() == Status.PENDING || queued.getStatus() == Status.STARTED ? queued.getProgress() : 100
        ).sum() / queue.size());
    }
    
}
