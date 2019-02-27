package codex.component.button;

import codex.editor.ArrStrEditor;
import java.awt.Color;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Общий интерфейс объектов: кнопок и меню для единообразных операций.
 */
public interface IButton {
    
    /**
     * Рамка по-умолчанию неактивной кнопки.
     */
    public final static Border EMPTY_BORDER = new EmptyBorder(1, 1, 1, 1);
    
    /**
     * Рамка по-умолчанию кнопки для подсветки в момент наведения мыши.
     */
    public final static Border HOVER_BORDER = new LineBorder(Color.decode("#C0DCF3"), 1);
    
    /**
     * Рамка по-умолчанию кнопки для подсветки в момент нажатия мыши.
     */
    public final static Border PRESS_BORDER = new LineBorder(Color.decode("#90C8F6"), 1);
    
    /**
     * Цвет фона по-умолчанию для подсветки кнопки в момент наведения мыши.
     */
    public final static Color  HOVER_COLOR  = Color.decode("#D8E6F2");
    
    /**
     * Цвет фона по-умолчанию для подсветки кнопки в момент нажатия мыши.
     */
    public final static Color  PRESS_COLOR  = Color.decode("#C0DCF3");
    
    /**
     * Установка внешнего слушателя события нажатия кнопки.
     * @param listener Реализация функционального интерфейса слушателя.
     */
    public void addActionListener(ActionListener listener);
    
    /**
     * Динамическая смена иконки кнопки. Следует использовать в случае когда в
     * зависимости от каких-либо условий изображение должно изменяться и необходимо
     * визуально сообщить пользователю о возможном смене поведения кнопки.
     * @see ArrStrEditor
     * @param icon Новая иконка кнопки.
     */
    public void setIcon(Icon icon);
    
    /**
     * Возвращает иконку кнопки.
     */
    public Icon getIcon();
    
    /**
     * Динамическая смена текста подсказки для кнопки. Следует использовать в случае 
     * когда в зависимости от каких-либо условий текст следует изменить.
     * @see ArrStrEditor
     * @param text Новый текст подсказки.
     */
    public void setHint(String text);
    
    /**
     * Динамическая смена надписи у кнопки.
     * @param text Текст новой надписи.
     */
    public void setText(String text);
    
    /**
     * Возвращает текст кнопки.
     */
    public String getText();
    
    /**
     * Переключения состояния интерактивности кнопки.
     * @param enabled TRUE - кнопка доступна для взаимодействия, иначе - нет.
     */
    public void setEnabled(boolean enabled);
    
    /**
     * Возвращает состояние интерактивности кнопки.
     * @return TRUE - кнопка доступна для взаимодействия, иначе - нет.
     */
    public boolean isEnabled();

    public void setVisible(boolean visible);
    
    /**
     * Программная эмуляция нажатия кнопки.
     */
    public void click();
    
}
