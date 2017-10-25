package codex.task;

import codex.component.render.DefaultRenderer;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
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
    
    private final static String  POOL_USAGE   = Language.get("thread@usage");
    private final static Matcher THREAD_NAME  = Pattern.compile("(^.*): .*").matcher("");
    private final static Matcher THREAD_STATE = Pattern.compile(".*: (.*$)").matcher("");
    
    private final Map<ITask, AbstractTaskView> taskRegistry = new HashMap<>();
    private final List<ExecutorService>        threadPool;
    
    private final JPanel            taskList;
    private final Consumer<ITask>   cancelAction;
    
    private final JProgressBar      poolUsage = new JProgressBar();
    private final JLabel            threadCount = new JLabel();
    private final DefaultTableModel threadTableModel;
    
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
    public TaskMonitor(JComponent invoker, List<ExecutorService> threadPool, Consumer<ITask> cancelAction) {
        super();
        setInvoker(invoker);
        setBorder(new MatteBorder(1, 1, 0, 1, Color.GRAY));
        this.threadPool = threadPool;
        
        // Панель задач
        taskList = new JPanel();
        taskList.setLayout(new BoxLayout(taskList, BoxLayout.Y_AXIS));
        taskList.add(Box.createVerticalGlue());

        JPanel taskPanel = new JPanel(new BorderLayout());
        taskPanel.add(taskList, BorderLayout.NORTH);
     
        JScrollPane taskScrollPane = new JScrollPane(taskPanel);
        taskScrollPane.getVerticalScrollBar().setUnitIncrement(15);
        taskScrollPane.getViewport().setBackground(Color.WHITE);
        taskScrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(2, 2, 1, 2),
                new LineBorder(Color.LIGHT_GRAY, 1)
        ));
        taskScrollPane.setColumnHeader(null);
        
        // Панель потоков
        JPanel threadPanel = new JPanel(new BorderLayout());
        threadPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
        poolUsage.setMinimum(0);
        poolUsage.setUI(new BasicProgressBarUI() {
            @Override
            protected Color getSelectionForeground() {
              return Color.WHITE;
            }
            
            @Override
            protected Color getSelectionBackground() {
                return Color.WHITE;
            }
                
        });
        poolUsage.setBackground(AbstractTaskView.PROGRESS_INFINITE);
        poolUsage.setForeground(AbstractTaskView.PROGRESS_ABORTED);
        poolUsage.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        poolUsage.setStringPainted(true);
        threadCount.setBorder(new EmptyBorder(0, 5, 0, 0));
        
        JPanel usagePanel = new JPanel(new BorderLayout());
        usagePanel.setOpaque(false);
        usagePanel.add(poolUsage, BorderLayout.WEST);
        usagePanel.add(threadCount, BorderLayout.CENTER);
        threadPanel.add(usagePanel, BorderLayout.NORTH);
        threadPanel.setBorder(new CompoundBorder(
                new EmptyBorder(2, 2, 1, 2),
                new CompoundBorder(
                    new LineBorder(Color.LIGHT_GRAY, 1),
                    new EmptyBorder(3, 3, 3, 3)
                )
        ));
        threadTableModel = new DefaultTableModel() {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        }; 
        JTable threadTable = new JTable(threadTableModel);
        threadTable.setDefaultRenderer(String.class, new DefaultRenderer());
        threadTable.setShowGrid(false);

        threadTableModel.addColumn("#"); 
        threadTableModel.addColumn(Language.get("thread@name"));
        threadTableModel.addColumn(Language.get("thread@status"));
        threadTable.setRowHeight((int ) (threadTable.getRowHeight() * 1.3));
        threadTable.getColumnModel().getColumn(0).setMaxWidth(40);
        threadTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        JScrollPane threadScrollPane = new JScrollPane(threadTable);
        threadScrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(3, 0, 0, 0),
                new LineBorder(Color.LIGHT_GRAY, 1)
        ));
        threadPanel.add(threadScrollPane, BorderLayout.CENTER);
        
        JTabbedPane tab = new JTabbedPane();
        tab.setBorder(new EmptyBorder(1, 1, 0, 1));
        tab.addTab(Language.get("tab@tasks"), ImageUtils.resize(ImageUtils.getByPath("/images/task.png"), 16, 16), taskScrollPane);
        tab.addTab(Language.get("tab@threads"), ImageUtils.resize(ImageUtils.getByPath("/images/thread.png"), 16, 16), threadPanel);
        add(tab);
        
        this.cancelAction = cancelAction;
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
        super.setVisible(visibility);
    }

    /**
     * Перерисовка окна и пересчет его позиции на эеране.
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
                if (context.getStatus() == Status.PENDING || context.getStatus() == Status.STARTED) {
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
        int active = threadPool.stream().mapToInt((executor) -> {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        }).sum() + (status == Status.PENDING || status == Status.STARTED ? 0 : -1);
        int total = threadPool.stream().mapToInt((executor) -> {
            return ((ThreadPoolExecutor) executor).getPoolSize();
        }).sum();
        poolUsage.setMaximum(total);
        poolUsage.setValue(active);
        threadCount.setText(MessageFormat.format(POOL_USAGE, active, total));
        
        
        Thread[] threadGroup = new Thread[256];
        Thread.enumerate(threadGroup);
        List<String> threadNames = Arrays.asList(threadGroup)
                .stream()
                .filter((thread) -> {
                    return thread != null && thread.getName().startsWith(TaskManager.class.getSimpleName());
                }).map((thread) -> {
                    return thread.getName();
                }).sorted().collect(Collectors.toList());
        SwingUtilities.invokeLater(() -> {
            while (threadTableModel.getRowCount() > 0) {
                threadTableModel.removeRow(0);
            }

            int threadIdx = 0; 
            for (String name : threadNames) {
                THREAD_NAME.reset(name).find();
                THREAD_STATE.reset(name).find();
                threadTableModel.addRow(new Object[]{threadIdx++, THREAD_NAME.group(1), THREAD_STATE.group(1)});
            }
        });
    }
    
}
