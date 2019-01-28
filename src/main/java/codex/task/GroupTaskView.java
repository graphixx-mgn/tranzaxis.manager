package codex.task;

import codex.component.button.IButton;
import codex.component.ui.StripedProgressBarUI;
import static codex.task.AbstractTaskView.PROGRESS_FINISHED;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
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
    
    private final Map<ITask, AbstractTaskView> views = new HashMap<>();
    
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
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(mainProgress, BorderLayout.WEST);
        
        IButton cancel = new CancelButton();
        cancel.addActionListener((event) -> {
            cancelAction.accept(main);
        });
        controls.add((JButton) cancel, BorderLayout.EAST);
        
        if (children.stream().anyMatch((subTask) -> {
            return subTask.isPauseable();
        })) {
            PauseButton pause = new PauseButton();
            pause.addActionListener(new AbstractAction() {
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    ITask running = children.stream().filter((subTask) -> {
                        return !subTask.getStatus().isFinal();
                    }).findFirst().get();
                    ((AbstractTask) running).setPause(running.getStatus() != Status.PAUSED);
                    pause.setState(running.getStatus() == Status.PAUSED);
                }
            });
            main.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status status) {
                    if (status.isFinal()) {
                        pause.setEnabled(false);
                    }
                }
            });
            controls.add(pause, BorderLayout.CENTER);
        }
        
        JPanel subTasks = new JPanel();
        subTasks.setLayout(new BoxLayout(subTasks, BoxLayout.Y_AXIS));
        subTasks.setBorder(new TitledBorder(Language.get("border@title")));
        subTasks.setBackground(Color.WHITE);
        
        new LinkedList<>(children).forEach((subTask) -> {
            AbstractTaskView view = subTask.createView(null);
            subTasks.add(view);
            views.put(subTask, view);
            subTask.addListener(this);
            statusChanged(subTask, subTask.getStatus());
        });
        
        add(mainTitle, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(subTasks, BorderLayout.AFTER_LAST_LINE);
        
        main.addListener(new ITaskListener() {
            
            @Override
            public void statusChanged(ITask task, Status status) {
                mainTitle.setIcon(mainTask.getStatus().getIcon());
                mainProgress.setForeground(
                    task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                        task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                            task.getStatus() == Status.CANCELLED ? PROGRESS_CANCELED :
                                PROGRESS_NORMAL
                );
                if (mainTask.getStatus() == Status.FINISHED || mainTask.getStatus() == Status.FINISHED) {
                    mainProgress.setString(TaskView.formatDuration(((AbstractTask) mainTask).getDuration()));
                }
            }
        });
    }

    @Override
    public void statusChanged(ITask task, Status status) {
        views.get(task).setBorder(new CompoundBorder(
                new EmptyBorder(new Insets(2, 5, 0, 0)), 
                new CompoundBorder(
                    new MatteBorder(0, 3, 0, 0, 
                            (task.getStatus() == Status.FAILED || task.getStatus() == Status.CANCELLED) ? PROGRESS_ABORTED :
                                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                                    task.getStatus() == Status.STARTED ? PROGRESS_INFINITE : 
                                    Color.GRAY
                    ),
                    new EmptyBorder(new Insets(0, 5, 0, 5))
                )
        ));
        progressChanged(task, task.getProgress(), task.getDescription());
    }

    @Override
    public void progressChanged(ITask task, int percent, String description) { 
        int totalProgress = views.keySet()
                .stream()
                .mapToInt((subTask) -> {
                    return subTask.getStatus() == Status.FINISHED ? 100 : subTask.getProgress();
                }).sum() / views.keySet().size();
        mainProgress.setValue(totalProgress);
        ((AbstractTask) mainTask).setProgress(totalProgress, null);
    }

}
