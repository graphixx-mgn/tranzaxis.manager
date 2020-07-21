package codex.unit;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.util.Locale;

/**
 * Абстракный класс модуля приложения.
 */
public abstract class AbstractUnit implements Iconified {
    
    protected JComponent view;
    
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
        return ImageUtils.getByPath(Language.get(getClass(), "unit.icon", Locale.US));
    }

    public String getTitle() {
        return Language.get(getClass(), "unit.title");
    }
    
}
