package codex.unit;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.util.LinkedList;
import java.util.List;

/**
 * Абстракный класс модуля приложения.
 */
public abstract class AbstractUnit implements Iconified, IEventInformer {
    
    protected JComponent view;
    private final List<IEventListener> eventListeners = new LinkedList<>();
    
    /**
     * Создает виджет модуля для размещение в окне приложения.
     */
    public abstract JComponent createViewport();
    /**
     * Вызывается в момент вставки виджета в окно приложения.
     */
    public void viewportBound() {}
    
    /**
     * Возвращает виджет модуля.
     */
    public final JComponent getViewport() {
        if (view == null) {
            view = createViewport();
        }
        return view;
    }

    @Override
    public ImageIcon getIcon() {
        return ImageUtils.getByPath(Language.get(getClass(), "unit.icon", Language.DEF_LOCALE));
    }

    public String getTitle() {
        return Language.get(getClass(), "unit.title");
    }

    @Override
    public void addEventListener(IEventListener listener) {
        eventListeners.add(listener);
    }

    @Override
    public void removeEventListener(IEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public List<IEventListener> getEventListeners() {
        return new LinkedList<>(eventListeners);
    }
}
