package codex.task;

import codex.component.ui.StripedProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalUnit;
import java.util.Date;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
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
        controls.add(progress, BorderLayout.WEST);
        
        if (cancelAction != null) {
            CancelButton cancel = new CancelButton();
            cancel.addActionListener((event) -> {
                cancelAction.accept(task);
            });
            controls.add(cancel, BorderLayout.EAST);
        }
        if (task.isPauseable() && cancelAction != null) {
            PauseButton pause = new PauseButton();
            pause.addActionListener(new AbstractAction() {
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    ((AbstractTask) task).setPause(task.getStatus() != Status.PAUSED);
                    pause.setState(task.getStatus() == Status.PAUSED);
                }
            });
            task.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status status) {
                    if (status.isFinal()) {
                        pause.setEnabled(false);
                    }
                }
            });
            controls.add(pause, BorderLayout.CENTER);
        }
        
        add(title,    BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(status,   BorderLayout.AFTER_LAST_LINE);
        
        task.addListener(this);
        
        updater = new Timer(1000, (ActionEvent event) -> {
            progress.setString(formatDuration(((AbstractTask) task).getDuration()));
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
        boolean isInfinitive = task.getStatus() == Status.STARTED && task.getProgress() == 0;
        
        if (task.getStatus() == Status.PAUSED) {
            updater.stop();
            progress.setString(Status.PAUSED.toString());
        } else if (!progress.isIndeterminate() && isInfinitive) {
            updater.start();
        } else if (progress.isIndeterminate() && !isInfinitive) {
            updater.stop();
        } else if (!isInfinitive && !task.getStatus().isFinal()) {
            progress.setString(null);
        } if (task.getStatus() == Status.FINISHED || task.getStatus() == Status.FINISHED) {
            progress.setString(formatDuration(((AbstractTask) task).getDuration()));
        }
        progress.setIndeterminate(isInfinitive);
        
        progress.setForeground(
            progress.isIndeterminate() ? PROGRESS_INFINITE : 
                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                    task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                        (task.getStatus() == Status.CANCELLED || task.getStatus() == Status.PAUSED) ? PROGRESS_CANCELED :
                            PROGRESS_NORMAL
        );
        status.setText(task.getDescription());
    }
    
    public final static long ONE_SECOND = 1000;
    public final static long ONE_MINUTE = ONE_SECOND * 60;
    public final static long ONE_HOUR   = ONE_MINUTE * 60;
    public final static long ONE_DAY    = ONE_HOUR   * 24;
    public static String formatDuration(long duration) {
        StringBuilder res = new StringBuilder();
        long temp;
        if (duration >= ONE_SECOND) {
            temp = duration / ONE_DAY;
            if (temp > 0) {
                duration -= temp * ONE_DAY;
                res.append(temp).append("d,");
            }
            
            if (temp > 0 || duration / ONE_HOUR > 0) {
                temp = duration / ONE_HOUR;
                duration -= temp * ONE_HOUR;
                res.append(String.format("%02d", temp)).append(":");
            }

            temp = duration / ONE_MINUTE;
            duration -= temp * ONE_MINUTE;
            res.append(String.format("%02d", temp)).append(":");

            temp = duration / ONE_SECOND;
            if (temp > 0) {
                res.append(String.format("%02d", temp));
            }
            return res.toString();
        } else {
            return "00:00";
        }
    }
    
}
