package codex.task;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Диалог отображения исполнения задач. Окно, содержащее виджеты исполняющихся 
 * в данный момент задач, запущенных методом {@link TaskManager#execute(codex.task.ITask)}.
 */
class TaskDialog extends Dialog implements ITaskListener {
    
    /**
     * Код выхода при нажатии кнопки перемещения задач в очередь.
     */
    public static final int           ENQUEUE    = 100;
    /**
     * Код выхода при нажатии кнопки отмены всех задач.
     */
    public static  final int          CANCEL     = 1;
    
    private static final DialogButton BTN_QUEUE  = new DialogButton(
            ImageUtils.resize(ImageUtils.getByPath("/images/enqueue.png"), 22, 22), Language.get("enqueue@title"), -1, ENQUEUE
    );
    private static final DialogButton BTN_CANCEL = new DialogButton(
            ImageUtils.resize(ImageUtils.getByPath("/images/cancel.png"), 22, 22), Language.get("cancel@title"), -1, CANCEL
    );
    
    private final JPanel viewPort;
    private final JPanel viewPanel;
    final Map<ITask, AbstractTaskView> taskRegistry = new ConcurrentHashMap<ITask, AbstractTaskView>() {
        
        @Override
        public AbstractTaskView put(ITask key, AbstractTaskView view) {
            viewPanel.add(view);
            super.put(key, view);
            SwingUtilities.invokeLater(() -> {
                setVisible(size() > 0);
            });
            return view;
        }

        @Override
        public void clear() {
            super.clear();
            SwingUtilities.invokeLater(() -> {
                setVisible(false);
            });
        }

        @Override
        public AbstractTaskView remove(Object key) {
            AbstractTaskView view = super.remove(key);
            SwingUtilities.invokeLater(() -> {
                setVisible(size() != 0);
            });
            return view;
        }
        
        
    };
    
    /**
     * Конструктор окна.
     * @param closeAction Слушатель события закрытия окна.
     */
    public TaskDialog(Window parent, ActionListener closeAction) {
        super(parent, 
                ImageUtils.getByPath("/images/progress.png"),
                Language.get("title"),
                new JPanel(),
                closeAction,
                BTN_QUEUE, BTN_CANCEL
        );
        
        viewPort = new JPanel();
        viewPort.setLayout(new BorderLayout());
        
        viewPanel = new JPanel();
        viewPanel.setLayout(new BoxLayout(viewPanel, BoxLayout.Y_AXIS));
        viewPanel.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY)
        ));
        viewPort.add(viewPanel, BorderLayout.NORTH);
        setContent(viewPort);
    }
    
    /**
     * Регистрация новой задачи.
     */
    void addTask(ITask task) {
        taskRegistry.put(task, task.createView(new Consumer<ITask>() {
            
            @Override
            public void accept(ITask context) {
                if (context.getStatus() == Status.PENDING || context.getStatus() == Status.STARTED) {
                    context.cancel(true);
                }
                viewPanel.remove(taskRegistry.get(context));
                taskRegistry.remove(context);
                statusChanged(null, null);
            }
        }));
        task.addListener(this);
    }
    
    /**
     * Очистка окна.
     */
    void clear() {
        taskRegistry.clear();
        viewPanel.removeAll();
    }

    @Override
    public void statusChanged(ITask task, Status status) {
        long running  = taskRegistry.keySet().stream()
                .filter(queued -> !queued.getStatus().isFinal())
                .count();
        long failed   = taskRegistry.keySet().stream()
                .filter(queued -> queued.getStatus() == Status.CANCELLED || queued.getStatus() == Status.FAILED)
                .count();
        boolean ready = running + failed == 0;
        
        BTN_QUEUE.setEnabled(running != 0);
        
        if (ready) {
            clear();
        }
    }
    
    @Override
    public void setVisible(boolean visible) {
        try {
            super.setVisible(visible);
        } catch (Throwable e) {}
    }
    
}
