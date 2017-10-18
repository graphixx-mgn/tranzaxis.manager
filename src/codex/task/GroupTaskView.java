package codex.task;

import codex.component.button.IButton;
import codex.component.ui.StripedProgressBarUI;
import static codex.task.AbstractTaskView.PROGRESS_FINISHED;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import javax.swing.border.TitledBorder;

/**
 * Реализация виджета групповой задачи для отображения в мониторе.
 */
final class GroupTaskView extends AbstractTaskView {

    private final ITask        mainTask;
    private final JLabel       mainTitle;
    private final JProgressBar mainProgress;
    
    private final Map<ITask, JLabel>       titles = new HashMap<>();
    private final Map<ITask, JLabel>       statuses = new HashMap<>();
    private final Map<ITask, JProgressBar> progresses = new HashMap<>();
    
    /**
     * Конструктор виджета.
     * @param title Заголовок группы задач для отображения в GUI.
     * @param main Ссылка на групповую задачу.
     * @param children Список дочерних задач.
     * @param cancelAction Действие по нажатии кнопки отмены на виджете задачи 
     * для обработки в мониторе.
     */
    GroupTaskView(String title, ITask main, List<ITask> children, Consumer<ITask> cancelAction) {
        super(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), 
            new EmptyBorder(new Insets(5, 5, 5, 5))
        ));
        
        mainTask = main;
        
        mainTitle = new JLabel(title, null, SwingConstants.LEFT);
        mainProgress = new JProgressBar();
        mainProgress.setMaximum(100);
        mainProgress.setUI(new StripedProgressBarUI(true));
        mainProgress.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        mainProgress.setStringPainted(true);
        
        IButton cancel = new CancelButton();
        cancel.addActionListener((event) -> {
            cancelAction.accept(main);
        });
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(mainProgress, BorderLayout.CENTER);
        controls.add((JButton) cancel, BorderLayout.EAST);
        
        JPanel subTasks = new JPanel(new GridLayout(0, 1));
        subTasks.setBorder(new TitledBorder(Language.get("border@title")));
        subTasks.setBackground(Color.WHITE);
        
        for (ITask subTask : new LinkedList<>(children)) {
            JPanel childPanel = new JPanel(new BorderLayout());
            childPanel.setBackground(Color.WHITE);
            childPanel.setBorder(new CompoundBorder(
                new EmptyBorder(new Insets(5, 5, 5, 5)),
                new MatteBorder(0, 0, 1, 0, Color.decode("#EEEEEE"))
            ));
            
            JLabel childTitle = new JLabel(subTask.getTitle(), null, SwingConstants.LEFT);
            titles.put(subTask, childTitle);
            
            JLabel childStatus = new JLabel();
            statuses.put(subTask, childStatus);
            
            JProgressBar childProgress = new JProgressBar();
            childProgress.setMaximum(100);
            childProgress.setUI(new StripedProgressBarUI(true));
            childProgress.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
            childProgress.setStringPainted(true);
            progresses.put(subTask, childProgress);
            
            childPanel.add(childTitle, BorderLayout.CENTER);
            childPanel.add(childProgress, BorderLayout.EAST);
            childPanel.add(childStatus, BorderLayout.AFTER_LAST_LINE);
            subTasks.add(childPanel);
            
            subTask.addListener(this);
            statusChanged(subTask, subTask.getStatus());
        }
        
        add(mainTitle, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(subTasks, BorderLayout.AFTER_LAST_LINE);
        
        main.addListener(new ITaskListener() {
            
            @Override
            public void statusChanged(ITask task, Status status) {
                mainProgress.setForeground(
                    task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                        task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                            task.getStatus() == Status.CANCELLED ? PROGRESS_CANCELED :
                                PROGRESS_NORMAL
                );
            }

            @Override
            public void progressChanged(ITask task, int percent, String description) {
                
            }
        });
    }

    @Override
    public void statusChanged(ITask task, Status status) {
        titles.get(task).setIcon(task.getStatus().getIcon());
        statuses.get(task).setText(task.getDescription());
        statuses.get(task).setForeground(
                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                    task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                        Color.GRAY
        );
        progressChanged(task, task.getProgress(), task.getDescription());
    }

    @Override
    public void progressChanged(ITask task, int percent, String description) {
        progresses.get(task).setValue(task.getStatus() == Status.FINISHED ? 100 : task.getProgress());
        progresses.get(task).setIndeterminate(task.getStatus() == Status.STARTED && task.getProgress() == 0);
        progresses.get(task).setForeground(
            progresses.get(task).isIndeterminate() ? PROGRESS_INFINITE : 
                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                    task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                        task.getStatus() == Status.CANCELLED ? PROGRESS_CANCELED :
                            PROGRESS_NORMAL
        );
        statuses.get(task).setText(task.getDescription());
        
        int totalProgress = progresses.keySet().stream().mapToInt(queued -> queued.getProgress()).sum() / progresses.keySet().size();
        
        mainProgress.setValue(totalProgress);
        ((AbstractTask) mainTask).setProgress(totalProgress, null);
    }
    
}
