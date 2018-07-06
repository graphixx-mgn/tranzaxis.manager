package codex.task;

import codex.component.button.IButton;
import codex.component.ui.StripedProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
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
        IButton cancel = new CancelButton();
        cancel.addActionListener((event) -> {
            cancelAction.accept(task);
        });
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.setBorder(new EmptyBorder(0, 5, 0, 0));
        controls.add(progress, BorderLayout.CENTER);
        controls.add((JButton) cancel, BorderLayout.EAST);
        
        add(title, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(status, BorderLayout.AFTER_LAST_LINE);
        
        task.addListener(this);
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
