package codex.task;

import codex.component.panel.ScrollablePanel;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.notification.INotificationService;
import codex.notification.NotificationService;
import codex.notification.NotifyCondition;
import codex.service.ServiceRegistry;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.TrayIcon;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.DefaultTableModel;

/**
 * Монитор исполнения задач. Popup окно, появляющееся при нажатии на панель задач 
 * {@link TaskStatusBar} и содержащее виджеты исполняющихся в данный момент задач.
 * @see TaskView
 * @see GroupTaskView
 */
final class TaskMonitor extends JPopupMenu implements ITaskListener {
    
    private final static String  NS_SOURCE    = "TaskManager/Task finished";

    private final Map<ITask, AbstractTaskView> taskRegistry = new HashMap<>();
    private final List<ExecutorService>        threadPool;
    
    private final ScrollablePanel   taskList;
    private final Consumer<ITask>   cancelAction;
    private boolean viewPortBound = false;
    
    static {
        UIManager.getDefaults().put("TabbedPane.contentBorderInsets", new Insets(0,0,0,0));
        UIManager.getDefaults().put("TabbedPane.tabsOverlapBorder", true);
    }

    /**
     * Конструктор монитора.
     * @param invoker Компонент GUI который вызывает отображение монитора. Необходим
     * для позиционирования окна.
     * @param cancelAction Действие по нажатии кнопки отмены на виджете задачи.
     */
    TaskMonitor(JComponent invoker, List<ExecutorService> threadPool, Consumer<ITask> cancelAction) {
        super();
        setInvoker(invoker);
        setBorder(new MatteBorder(1, 1, 0, 1, Color.GRAY));
        this.threadPool = threadPool;

        taskList = new ScrollablePanel();
        taskList.setLayout(new BoxLayout(taskList, BoxLayout.Y_AXIS));
        taskList.add(Box.createVerticalGlue());
        taskList.setScrollableWidth(ScrollablePanel.ScrollableSizeHint.FIT);

        JScrollPane taskScrollPane = new JScrollPane(taskList);        
        taskScrollPane.getViewport().setBackground(Color.decode("#F5F5F5"));
        taskScrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(2, 2, 1, 2),
                new LineBorder(Color.LIGHT_GRAY, 1)
        ));
        taskScrollPane.setColumnHeader(null);
        add(taskScrollPane);
        
        this.cancelAction = cancelAction;
        ServiceRegistry.getInstance().addRegistryListener(NotificationService.class, (service) -> {
            ((NotificationService) service).registerSource(NS_SOURCE, NotifyCondition.INACTIVE);
        });
    }

    /**
     * Переключение состояния видимости окна.
     */
    @Override
    public void setVisible(boolean visibility) {
        if (visibility) {
            repaint();
            Point invokerLocation = getInvoker().getLocationOnScreen();
            setLocation(invokerLocation.x + 5, invokerLocation.y - getPreferredSize().height);
        }
        if (!viewPortBound && FocusManager.getCurrentManager().getActiveWindow() != null) {
            viewPortBound = true;
            FocusManager.getCurrentManager().getActiveWindow().addComponentListener(new ComponentAdapter() {
                
                @Override
                public void componentResized(ComponentEvent ev) {
                    setVisible(visibility);
                }
            });
        }
        super.setVisible(visibility);
    }

    /**
     * Перерисовка окна и пересчет его позиции на экране.
     */
    @Override
    public void repaint() {
        if (getInvoker() != null) {
            setPreferredSize(new Dimension(
                getInvoker().getSize().width - 4, 400
            ));
            pack();
        }
        super.repaint();
    }
    
    /**
     * Регистрация новой задачи в мониторе и добавление её виджета в окно.
     */
    void registerTask(ITask task) {
        task.addListener(this);
        taskRegistry.put(task, task.createView(new Consumer<ITask>() {
            
            @Override
            public void accept(ITask context) {
                if (!context.getStatus().isFinal()) {
                    context.cancel(true);
                } else {
                    taskList.remove(taskRegistry.get(context));
                    cancelAction.accept(context);
                    unregisterTask(context);
                }
            }
        }));
        taskList.add(taskRegistry.get(task));
    }
    
    /**
     * Удаление задачи из монитора и её виджета из окна.
     */
    void unregisterTask(ITask task) {
        if (taskRegistry.containsKey(task)) {
            taskList.remove(taskRegistry.get(task));
            taskRegistry.remove(task);
        }
    }
    
    /**
     * Очистка списка задач и окна виджетов.
     */
    void clearRegistry() {
        taskRegistry.keySet().stream().forEach((task) -> {
            taskList.remove(taskRegistry.get(task));
        });
        taskRegistry.clear();
    }

    @Override
    public void statusChanged(ITask task, Status status) {
        if (task.getStatus() == Status.FAILED || task.getStatus() == Status.FINISHED) {
            String msgTitle = Language.get(
                    TaskMonitor.class.getSimpleName(),
                    "notify@"+task.getStatus().name().toLowerCase()
            );
            ((INotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class)).showMessage(
                    NS_SOURCE,
                    msgTitle, 
                    task.getTitle(), 
                    task.getStatus() == Status.FINISHED ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.ERROR
            );
        }
    }
 
}
