package codex.task;

import codex.component.ui.StripedProgressBarUI;
import codex.utils.Language;
import com.sun.javafx.PlatformUtil;
import org.bridj.Pointer;
import org.bridj.cpp.com.COMRuntime;
import org.bridj.cpp.com.shell.ITaskbarList3;
import org.bridj.jawt.JAWTUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Виджет модуля {@link TaskManager}, представляет собой панель задач с информацией о 
 * количестве исполняемых в данный момент задач, а также из статусы и общий прогресс.
 * Скрывается, если задач нет или все выполнены успешно. При нажатии вызывается 
 * окно просмотра задач {@link TaskMonitor}.
 */
final class TaskStatusBar extends JPanel implements ITaskListener {
    
    private final String PATTERN_NORMAL = Language.get(Status.class, "total@normal");
    private final String PATTERN_ERRORS = Language.get(Status.class, "total@errors");
    
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
        setBorder(new EmptyBorder(1, 2, 1, 2));
        
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
        progress.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        
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
        List<ITask> taskList = new LinkedList<>(queue);  
        long running  = taskList.stream().filter(queued -> !queued.getStatus().isFinal()).count();
        long stopped  = taskList.stream().filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED).count();
        long failed   = taskList.stream().filter(queued -> queued.getStatus() == Status.FAILED).count();
        boolean ready = running + stopped == 0;
        
        status.setVisible(!ready);
        progress.setVisible(!ready);
        clear.setVisible(!ready);
        clear.setEnabled(running == 0);
        
        if (ready) {
            progress.setValue(0);
            monitor.clearRegistry();
            queue.clear();
            if (PlatformUtil.isWin7OrLater() && SwingUtilities.getWindowAncestor(this) != null) {
                try {
                    ITaskbarList3 taskBarIcon = COMRuntime.newInstance(ITaskbarList3.class);
                    long hwndVal = JAWTUtils.getNativePeerHandle(SwingUtilities.getWindowAncestor(this));
                    Pointer<?> HWND = Pointer.pointerToAddress(hwndVal);
                    taskBarIcon.SetProgressState((Pointer) HWND, ITaskbarList3.TbpFlag.TBPF_NOPROGRESS);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        
        long finished = taskList.stream().filter(queued -> queued.getStatus() == Status.FINISHED).count();
        status.setText(MessageFormat.format(stopped > 0 ? PATTERN_ERRORS : PATTERN_NORMAL, running, finished, stopped));
        if (task != null) {
            progressChanged(task, task.getProgress(), task.getDescription());
        }
        
        if (PlatformUtil.isWin7OrLater() && SwingUtilities.getWindowAncestor(this) != null && SwingUtilities.getWindowAncestor(this).isShowing()) {
            try {
                ITaskbarList3 taskBarIcon = COMRuntime.newInstance(ITaskbarList3.class);
                long hwndVal = JAWTUtils.getNativePeerHandle(SwingUtilities.getWindowAncestor(this));
                Pointer<?> HWND = Pointer.pointerToAddress(hwndVal);
                taskBarIcon.SetProgressState(
                        (Pointer) HWND,
                        failed > 0 ? ITaskbarList3.TbpFlag.TBPF_ERROR : ITaskbarList3.TbpFlag.TBPF_NORMAL
                );
            } catch (Throwable e) {
                //
            }
        }
    }

    @Override
    public void progressChanged(ITask task, int percent, String description) {
        List<ITask> taskList = new LinkedList<>(queue); 
        int prevProgress = progress.getValue();
        progress.setValue(taskList.stream().mapToInt(
                queued -> !queued.getStatus().isFinal() ? queued.getProgress() : 100
        ).sum() / taskList.size());
      
        if (prevProgress != progress.getValue() && PlatformUtil.isWin7OrLater() && SwingUtilities.getWindowAncestor(this) != null) {
            try {
                ITaskbarList3 taskBarIcon = COMRuntime.newInstance(ITaskbarList3.class);
                long hwndVal = JAWTUtils.getNativePeerHandle(SwingUtilities.getWindowAncestor(this));
                Pointer<?> HWND = Pointer.pointerToAddress(hwndVal);
                taskBarIcon.SetProgressValue((Pointer) HWND, progress.getValue(), 100);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    
}
