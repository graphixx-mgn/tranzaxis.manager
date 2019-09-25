package codex.task;

import java.awt.Color;
import java.awt.LayoutManager;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Абстрактный класс виджета задачи.
 */
public abstract class AbstractTaskView extends JPanel implements ITaskListener {

    final static Color PROGRESS_FINISHED = Color.decode("#4CAE32");
    final static Color PROGRESS_ABORTED  = Color.decode("#D93B3B");
    final static Color PROGRESS_CANCELED = Color.GRAY;
    
    /**
     * Конструктор виджета.
     * @param layout Менеджер компоновки элементов GUI.
     */
    public AbstractTaskView(LayoutManager layout) {
        super(layout);
    }
    
}
