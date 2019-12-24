package codex.task;

import codex.component.button.IButton;
import codex.component.ui.StripedProgressBarUI;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
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
final class GroupTaskView<T extends ITask> extends AbstractTaskView {

    private final GroupTask    mainTask;
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
    GroupTaskView(String title, GroupTask main, List<T> children, Consumer<ITask> cancelAction) {
        super(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), 
            new EmptyBorder(new Insets(5, 5, 5, 5))
        ));
        
        mainTask = main;
        
        mainTitle = new JLabel(title, null, SwingConstants.LEFT);
        mainTitle.setBorder(new EmptyBorder(0, 0, 0, 5));
        mainProgress = new JProgressBar();
        mainProgress.setMaximum(100);
        mainProgress.setUI(new StripedProgressBarUI(true));
        mainProgress.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        mainProgress.setStringPainted(true);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(mainProgress, BorderLayout.WEST);
        
        IButton cancel = new CancelButton();
        cancel.addActionListener((event) -> cancelAction.accept(main));
        controls.add((JButton) cancel, BorderLayout.EAST);
        
        if (children.stream().anyMatch(ITask::isPauseable)) {
            PauseButton pause = new PauseButton();
            pause.addActionListener(new AbstractAction() {
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    children.stream()
                            .filter((subTask) -> !subTask.getStatus().isFinal())
                            .findFirst()
                            .ifPresent(running -> {
                                ((AbstractTask) running).setPause(running.getStatus() != Status.PAUSED);
                                pause.setState(running.getStatus() == Status.PAUSED);
                            });
                }
            });
            main.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                    if (nextStatus.isFinal()) {
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
            final AbstractTaskView view;
            if (subTask instanceof GroupTask) {
                view = new TaskView(subTask, null);
                GroupTask group = (GroupTask) subTask;
                String summaryPattern  = Language.get(GroupTaskView.class, "desc@summary");

                group.getSequence().forEach(inGroupTask -> {
                    inGroupTask.addListener(new ITaskListener() {
                        @Override
                        public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                            progressChanged(task, task.getProgress(), task.getDescription());
                        }

                        @Override
                        public void progressChanged(ITask task, int percent, String description) {
                            int totalProgress = group.getSequence().stream()
                                    .mapToInt(inGroupTask -> inGroupTask.getStatus() == Status.FINISHED ? 100 : inGroupTask.getProgress()).sum() / group.getSequence().size();
                            int finishedTasks = group.getSequence().stream()
                                    .mapToInt(inGroupTask -> inGroupTask.getStatus() == Status.FINISHED ? 1 : 0)
                                    .sum();

                            group.setProgress(
                                    totalProgress,
                                    MessageFormat.format(
                                            summaryPattern,
                                            finishedTasks+1,
                                            group.getSequence().size(),
                                            inGroupTask.getDescription()
                                    )
                            );
                        }
                    });
                });
            } else {
                view = subTask.createView(null);
            }

            subTasks.add(view);
            subTask.addListener(this);
            views.put(subTask, view);
            statusChanged(subTask, subTask.getStatus(), subTask.getStatus());
        });
        
        add(mainTitle, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(subTasks, BorderLayout.AFTER_LAST_LINE);
        
        main.addListener(new ITaskListener() {

            {
                statusChanged(main, main.getStatus(), main.getStatus());
            }

            @Override
            public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                SwingUtilities.invokeLater(() -> {
                    mainTitle.setIcon(mainTask.getStatus().getIcon());
                    mainProgress.setForeground(
                            task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                                    task.getStatus() == Status.FAILED ? PROGRESS_ABORTED :
                                            task.getStatus() == Status.CANCELLED ? PROGRESS_CANCELED :
                                                    StripedProgressBarUI.PROGRESS_NORMAL
                    );
                    if (mainTask.getStatus() == Status.FINISHED) {
                        mainProgress.setString(TaskView.formatDuration(mainTask.getDuration()));
                    }
                });
            }
        });
    }

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        views.get(task).setBorder(new CompoundBorder(
                new EmptyBorder(new Insets(2, 5, 0, 0)),
                new CompoundBorder(
                    new MatteBorder(0, 3, 0, 0,
                            (task.getStatus() == Status.FAILED || task.getStatus() == Status.CANCELLED) ? PROGRESS_ABORTED :
                                task.getStatus() == Status.FINISHED ? PROGRESS_FINISHED :
                                    task.getStatus() == Status.STARTED ? StripedProgressBarUI.PROGRESS_INFINITE :
                                    Color.GRAY
                    ),
                    new EmptyBorder(new Insets(0, 5, 0, 5))
                )
        ));
        progressChanged(task, task.getProgress(), task.getDescription());
    }

    @Override
    public void progressChanged(ITask task, int percent, String description) {
        int totalProgress = mainTask.getSequence().stream()
                .mapToInt(subTask -> {
                    if (subTask.getStatus() == Status.FINISHED || (subTask.getStatus() == Status.FAILED && !mainTask.isStopOnError())) {
                        return 100;
                    } else {
                         return subTask.getProgress();
                    }
                }).sum() / mainTask.getSequence().size();
        mainProgress.setValue(totalProgress);
        mainTask.setProgress(totalProgress, null);
    }

}
