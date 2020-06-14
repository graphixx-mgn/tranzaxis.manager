package codex.task;

import codex.component.ui.StripedProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 * Реализация виджета задачи для отображения в мониторе.
 */
public class TaskView extends AbstractTaskView {
    
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
    public TaskView(ITask task, Consumer<ITask> cancelAction) {
        super(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), 
            new EmptyBorder(new Insets(5, 5, 5, 5))
        ));
        
        title  = new JLabel(task.getTitle(), null, SwingConstants.LEFT);
        status = new JLabel(task.getDescription());
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
            cancel.addActionListener((event) -> cancelAction.accept(task));
            controls.add(cancel, BorderLayout.EAST);
        }
        if (task.isPauseable() && cancelAction != null) {
            PauseButton pause = new PauseButton();
            pause.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ((AbstractTask) task).setPause(task.getStatus() != Status.PAUSED);
                    SwingUtilities.invokeLater(() -> pause.setState(task.getStatus() == Status.PAUSED));
                }
            });
            task.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                    if (nextStatus.isFinal()) {
                        SwingUtilities.invokeLater(() -> pause.setEnabled(false));
                    }
                }
            });
            controls.add(pause, BorderLayout.CENTER);
        }

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                progressChanged(task, task.getProgress(), task.getDescription());
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {}

            @Override
            public void ancestorMoved(AncestorEvent event) {}
        });
        
        add(title,    BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(status,   BorderLayout.SOUTH);
        
        task.addListener(this);
        
        updater = new Timer(1000, (ActionEvent event) -> progress.setString(formatDuration(((AbstractTask) task).getDuration())));
        updater.setInitialDelay(0);
        statusChanged(task, task.getStatus(), task.getStatus());
    }
    
    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        SwingUtilities.invokeLater(() -> {
            title.setIcon(task.getStatus().getIcon());
            status.setText(task.getDescription());
            status.setForeground(
                    task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                            task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                                    Color.GRAY
            );
        });
        progressChanged(task, task.getProgress(), task.getDescription());
    }
    
    @Override
    public void progressChanged(ITask task, int percent, String description) {
        if (isDisplayable()) {
            SwingUtilities.invokeLater(() -> {
                progress.setValue(task.getStatus() == Status.FINISHED ? 100 : task.getProgress());
                boolean isInfinitive = task.getStatus() == Status.STARTED && task.getProgress() == 0;

                if (task.getStatus() == Status.PAUSED) {
                    updater.stop();
                    progress.setString(Status.PAUSED.toString());
                } else if (!progress.isIndeterminate() && isInfinitive) {
                    updater.start();
                } else if (progress.isIndeterminate() && !isInfinitive) {
                    updater.stop();
                    progress.setString(null);
                } else if (!isInfinitive && !task.getStatus().isFinal()) {
                    progress.setString(null);
                }
                if (task.getStatus() == Status.FINISHED || task.getStatus() == Status.FINISHED) {
                    progress.setString(formatDuration(((AbstractTask) task).getDuration()));
                }
                progress.setIndeterminate(isInfinitive);

                progress.setForeground(
                        progress.isIndeterminate() ? StripedProgressBarUI.PROGRESS_INFINITE :
                                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                                        task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                                                (task.getStatus() == Status.CANCELLED || task.getStatus() == Status.PAUSED) ? PROGRESS_CANCELED :
                                                        StripedProgressBarUI.PROGRESS_NORMAL
                );
                status.setText(task.getDescription());
            });
        }
    }
    
    private final static long ONE_SECOND = 1000;
    private final static long ONE_MINUTE = ONE_SECOND * 60;
    private final static long ONE_HOUR   = ONE_MINUTE * 60;
    private final static long ONE_DAY    = ONE_HOUR   * 24;
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
            res.append(String.format("%02d", temp));
            return res.toString();
        } else {
            return "00:00";
        }
    }
    
}
