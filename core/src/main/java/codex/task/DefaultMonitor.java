package codex.task;

import codex.component.dialog.Dialog;
import javax.swing.*;
import java.util.LinkedList;
import java.util.List;

class DefaultMonitor implements ITaskMonitor {

    private TaskDialog   taskDialog;
    private ITaskMonitor taskRecipient;
    final private List<ITask> taskList = new LinkedList<>();

    DefaultMonitor() {}

    @Override
    public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        synchronized (taskList) {
            long running = taskList.stream().filter(queued -> !queued.getStatus().isFinal()).count();
            long stopped = taskList.stream().filter(queued ->  queued.getStatus() == Status.FAILED).count();

            if (running == 0) {
                if (stopped == 0) {
                    clearRegistry();
                } else {
                    new LinkedList<>(taskList).stream()
                            .filter(queued -> queued.getStatus().equals(Status.FINISHED))
                            .forEach(this::unregisterTask);
                    SwingUtilities.invokeLater(() -> taskDialog.pack());
                }
            }
        }
    }

    @Override
    public synchronized void registerTask(ITask task) {
        task.addListener(this);
        if (taskDialog == null) {
            taskDialog = new TaskDialog(
                    taskRecipient != null,
                    (button) -> (keyEvent) -> {
                        int ID = button == null ? Dialog.EXIT : button.getID();
                        if (ID == Dialog.CANCEL || ID == Dialog.EXIT) {
                            clearRegistry();
                        }
                        if (ID == Dialog.OK) {
                            synchronized (taskList) {
                                new LinkedList<>(taskList).forEach(ctxTask -> {
                                    if (!ctxTask.getStatus().isFinal()) {
                                        taskRecipient.registerTask(ctxTask);
                                    }
                                    unregisterTask(ctxTask);
                                });
                                SwingUtilities.invokeLater(() -> taskDialog.setVisible(false));
                            }
                        }
                    });
        }

        synchronized (taskList) {
            taskList.add(task);
        }
        SwingUtilities.invokeLater(() -> {
            taskDialog.insertTask(task, ctxTask -> {
                if (!ctxTask.getStatus().isFinal()) {
                    ctxTask.cancel(true);
                }
                unregisterTask(ctxTask);

                synchronized (taskList) {
                    if (!taskList.isEmpty()) {
                        SwingUtilities.invokeLater(() -> taskDialog.pack());
                    }
                }
            });

            taskDialog.pack();
            if (!taskDialog.isVisible()) {
                taskDialog.setVisible(true);
                taskDialog = null;
            }
        });
    }

    @Override
    public synchronized void unregisterTask(ITask task) {
        task.removeListener(this);
        synchronized (taskList) {
            if (taskList.remove(task)) {
                SwingUtilities.invokeLater(() -> taskDialog.removeTask(task));
            }
        }
    }

    @Override
    public synchronized void clearRegistry() {
        final List<ITask> tasks;
        synchronized (taskList) {
            tasks = new LinkedList<>(taskList);
        }
        tasks.forEach(ctxTask -> {
            unregisterTask(ctxTask);
            if (!ctxTask.getStatus().isFinal()) {
                ctxTask.cancel(true);
            }
        });
        SwingUtilities.invokeLater(() -> taskDialog.setVisible(false));
    }

    @Override
    public void setTaskRecipient(ITaskMonitor monitor) {
        this.taskRecipient = monitor;
    }
}
