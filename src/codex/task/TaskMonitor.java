package codex.task;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

/**
 * Монитор исполнения задач. Popup окно, появляющееся при нажатии на панель задач 
 * {@link TaskStatusBar} и содержащее виджеты исполняющихся в данный момент задач.
 * @see TaskView
 * @see GroupTaskView
 */
final class TaskMonitor extends JPopupMenu {
    
    private final Map<ITask, AbstractTaskView> taskRegistry = new HashMap<>();
    private final JPanel viewPanel;
    private final Consumer<ITask> cancelAction;

    /**
     * Конструктор монитора.
     * @param invoker Компонент GUI который вызывает отображение монитора. Необходим
     * для позиционирования окна.
     * @param cancelAction Действие по нажатии кнопки отмены на виджете задачи.
     */
    public TaskMonitor(JComponent invoker, Consumer<ITask> cancelAction) {
        super();
        setInvoker(invoker);
        setBorder(new MatteBorder(1, 1, 0, 1, Color.GRAY));
        
        viewPanel = new JPanel();
        viewPanel.setLayout(new BoxLayout(viewPanel, BoxLayout.Y_AXIS));
        viewPanel.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(viewPanel, BorderLayout.NORTH);
        
        JScrollPane scrollPanel = new JScrollPane(wrapper);
        scrollPanel.getVerticalScrollBar().setUnitIncrement(15);
        scrollPanel.getViewport().setBackground(Color.WHITE);
        scrollPanel.setBorder(new CompoundBorder(
                new EmptyBorder(2, 2, 1, 2),
                new LineBorder(Color.LIGHT_GRAY, 1)
        ));
        scrollPanel.setColumnHeader(null);
        add(scrollPanel);
        
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
        taskRegistry.put(task, task.createView(new Consumer<ITask>() {
            
            @Override
            public void accept(ITask context) {
                if (context.getStatus() == Status.PENDING || context.getStatus() == Status.STARTED) {
                    context.cancel(true);
                } else {
                    viewPanel.remove(taskRegistry.get(context));
                    cancelAction.accept(context);
                    unregisterTask(context);
                }
            }
        }));
        viewPanel.add(taskRegistry.get(task));
    }
    
    /**
     * Удаление задачи из монитора и её виджета из окна.
     */
    void unregisterTask(ITask task) {
        if (taskRegistry.containsKey(task)) {
            viewPanel.remove(taskRegistry.get(task));
            taskRegistry.remove(task);
        }
    }
    
    /**
     * Очистка списка задач и окна виджетов.
     */
    void clearRegistry() {
        taskRegistry.keySet().stream().forEach((task) -> {
            viewPanel.remove(taskRegistry.get(task));
        });
        taskRegistry.clear();
    }
    
}
