package codex.unit;

import javax.swing.JComponent;

/**
 * Абстракный класс модуля приложения.
 */
public abstract class AbstractUnit {
    
    protected JComponent view;
    
    /**
     * Создает виджет модуля для размещение в окне приложения.
     */
    public abstract JComponent createViewport();
    /**
     * Вызывается в момент вставки виджета в окно приложения.
     */
    public void viewportBound() {};
    
    /**
     * Возвращает виджет модуля.
     */
    public final JComponent getViewport() {
        if (view == null) {
            view = createViewport();
        }
        return view;
    };
    
}
