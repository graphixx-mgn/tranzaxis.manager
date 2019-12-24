package codex.task;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Диалог отображения исполнения задач. Окно, содержащее виджеты исполняющихся 
 * в данный момент задач, запущенных методом {@link TaskExecutorService#executeTask(codex.task.ITask)}.
 */
class TaskDialog extends Dialog {

    private static final int MIN_WIDTH = 600;

    private final Map<ITask, AbstractTaskView> taskViews = new HashMap<>();
    private final JPanel taskViewList;
    private final boolean enableMovement;

    /**
     * Конструктор окна.
     * //@param closeAction Слушатель события закрытия окна.
     */
    TaskDialog(boolean enableMovement, Function<DialogButton, ActionListener> handler) {
        super(
                Dialog.findNearestWindow(),
                ImageUtils.getByPath("/images/progress.png"),
                Language.get("title"),
                new JPanel(),
                null,
                Stream.concat(
                        enableMovement ?
                                Stream.of(Default.BTN_OK.newInstance(
                                    ImageUtils.getByPath("/images/enqueue.png"),
                                    Language.get("enqueue@title")
                                )) :
                                Stream.empty(),
                        Stream.of(Default.BTN_CANCEL.newInstance())
                ).toArray(DialogButton[]::new)
        );
        setResizable(false);

        this.handler = handler;
        this.enableMovement = enableMovement;

        JPanel viewPort = new JPanel();
        viewPort.setLayout(new BorderLayout());

        taskViewList = new JPanel();
        taskViewList.setLayout(new BoxLayout(taskViewList, BoxLayout.Y_AXIS));
        taskViewList.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY)
        ));
        viewPort.add(taskViewList, BorderLayout.NORTH);
        setContent(viewPort);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        return new Dimension(Math.max(preferred.width, MIN_WIDTH), preferred.height);
    }

    void insertTask(ITask task, Consumer<ITask> handler) {
        synchronized (taskViewList.getTreeLock()) {
            taskViews.put(task, task.createView(handler));
            taskViewList.add(taskViews.get(task));
        }
    }

    void removeTask(ITask task) {
        synchronized (taskViewList.getTreeLock()) {
            if (taskViews.containsKey(task)) {
                try {
                    taskViewList.remove(taskViews.remove(task));
                } catch (Throwable e) {
                    //
                }
                if (enableMovement) {
                    getButton(Dialog.OK).setEnabled(
                            taskViews.keySet().stream().anyMatch(ctxTask -> !ctxTask.getStatus().isFinal())
                    );
                }
            }
        }
    }
}
