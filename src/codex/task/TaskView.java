package codex.task;

import codex.component.button.IButton;
import codex.component.ui.StripedProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

/**
 * Реализация виджета задачи для отображения в мониторе.
 */
final class TaskView extends AbstractTaskView {
    
    private final JLabel title;
    private final JLabel status;
    private final Timer  updater;
    private final JProgressBar progress;
    
    private LocalDateTime startTime;
    
    /**
     * Конструктор виджета.
     * @param task Ссылка на задачу.
     * @param cancelAction Действие по нажатии кнопки отмены на виджете задачи 
     * для обработки в мониторе.
     */
    TaskView(ITask task, Consumer<ITask> cancelAction) {
        super(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), 
            new EmptyBorder(new Insets(5, 5, 5, 5))
        ));
        
        title  = new JLabel(task.getTitle(), null, SwingConstants.LEFT);
        status = new JLabel();
        progress = new JProgressBar();
        progress.setMaximum(100);
        progress.setUI(new StripedProgressBarUI(true));
        progress.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        progress.setStringPainted(true);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.setBorder(new EmptyBorder(0, 5, 0, 0));
        controls.add(progress, BorderLayout.CENTER);
        
        if (cancelAction != null) {
            IButton cancel = new CancelButton();
            cancel.addActionListener((event) -> {
                cancelAction.accept(task);
            });
            controls.add((JButton) cancel, BorderLayout.EAST);
        }
        
        add(title,    BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(status,   BorderLayout.AFTER_LAST_LINE);
        
        task.addListener(this);
        
        updater = new Timer(1000, (ActionEvent event) -> {
            LocalDateTime curTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, curTime).toMillis();
        
            SimpleDateFormat format = new SimpleDateFormat("mm:ss", java.util.Locale.getDefault());
            progress.setString(format.format(new Date(duration)));
        });
        updater.setInitialDelay(0);
        statusChanged(task, task.getStatus());
    }
    
    @Override
    public void statusChanged(ITask task, Status taskStatus) {
        title.setIcon(task.getStatus().getIcon());
        status.setText(task.getDescription());
        status.setForeground(
                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                    task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                        Color.GRAY
        );
        progressChanged(task, task.getProgress(), task.getDescription());
    }
    
    @Override
    public void progressChanged(ITask task, int percent, String description) {
        progress.setValue(task.getStatus() == Status.FINISHED ? 100 : task.getProgress());
        
        if (!progress.isIndeterminate() && task.getStatus() == Status.STARTED && task.getProgress() == 0) {
            startTime = LocalDateTime.now();
            updater.start();
        }
        if (progress.isIndeterminate() && !(task.getStatus() == Status.STARTED && task.getProgress() == 0)) {
            updater.stop();
            progress.setString(null);
        }
        progress.setIndeterminate(task.getStatus() == Status.STARTED && task.getProgress() == 0);

        progress.setForeground(
            progress.isIndeterminate() ? PROGRESS_INFINITE : 
                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                    task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                        task.getStatus() == Status.CANCELLED ? PROGRESS_CANCELED :
                            PROGRESS_NORMAL
        );
        status.setText(task.getDescription());
    }
    
}
