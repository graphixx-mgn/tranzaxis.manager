package codex.unit;

import javax.swing.JComponent;

/**
 * Абстракный класс модуля приложения.
 */
public abstract class AbstractUnit {
    
    protected final JComponent view = createViewport();
    
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
        return view;
    };
    
}
