package codex.task;

import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import javax.swing.*;

/**
 * Модуль-исполнитель задач {@link ITask}.
 */
public final class TaskManager extends AbstractUnit {
    
    private TaskStatusBar statusBar = new TaskStatusBar();

//    private TaskDialog taskDialog = new TaskDialog(
//            null,
//            new AbstractAction() {
//                @Override
//                public void actionPerformed(ActionEvent event) {
//                    long running = taskDialog.runningTasks();
//                    if (event.getID() == TaskDialog.CANCEL || event.getID() == Dialog.EXIT) {
//                        taskDialog.taskRegistry.keySet().forEach((task) -> task.cancel(true));
//                    } else if (event.getID() == TaskDialog.ENQUEUE && running != 0) {
//                        taskDialog.taskRegistry.keySet().forEach((task) -> {
//                            taskDialog.removeTask(task);
//                            taskPanel.addTask(task);
//                        });
//                    }
//                }
//            }
//    ) {
//        @Override
//        public void setLocationRelativeTo(Component c) {
//            Window owner = IComplexType.coalesce(
//                    FocusManager.getCurrentManager().getActiveWindow(),
//                    SwingUtilities.getWindowAncestor(getViewport())
//            );
//            super.setLocationRelativeTo(owner);
//        }
//    };
    
    /**
     * Конструктор.
     */
    public TaskManager() {
        Logger.getLogger().debug("Initialize unit: Task Manager");
        ServiceRegistry.getInstance().addRegistryListener(ITaskExecutorService.class, service -> {
            ((TaskExecutorService) service).attachMonitor(ThreadPoolKind.Queued, statusBar.getMonitor());
        });
    }

    @Override
    public JComponent createViewport() {
        return statusBar;
    }

}
