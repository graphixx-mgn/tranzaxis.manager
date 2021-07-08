package codex.editor;

import codex.command.EditorCommand;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.List;

/**
 * Интерфейс GUI редакторов свойств {@link PropertyHolder}
 * @param <T> Тип внутреннего значения свойства.
 */
public interface IEditor<T extends IComplexType<V, ? extends IMask<V>>, V> {
    
    /**
     * Стандартный цвет шрифта для отрисовки значения свойств.
     */
    Color COLOR_NORMAL = UIManager.getDefaults().getColor("textText");
    
    /**
     * Стандартный цвет шрифта для отрисовки не редактиремых свойств.
     */
    Color COLOR_INACTIVE = SystemColor.textInactiveText;
    
    /**
     * Цвет шрифта для отрисовки значения заблокированных свойств.
     */
    Color COLOR_DISABLED = SystemColor.textInactiveText;
    
    /**
     * Цвет шрифта для отрисовки значения свойств с ошибочным значением.
     */
    Color COLOR_INVALID = Color.RED;
    
    /**
     * Стандартный шрифт наименований свойств.
     */
    Font FONT_NORMAL = UIManager.getDefaults().getFont("Label.font");
    
    /**
     * Шрифт наименования обязательного свойства с пустым значением.
     * @see PropertyHolder#isValid()
     */
    Font FONT_BOLD   = FONT_NORMAL.deriveFont(Font.BOLD);
    
    /**
     * Стандартный шрифт отображения значений свойств.
     */
    Font FONT_VALUE = new Font("SansSerif", Font.PLAIN, (int) (UIManager.getDefaults().getFont("Label.font").getSize() * 1.3));
    
    /**
     * Стандартный бордюр редактора.
     */
    Border BORDER_NORMAL = new CompoundBorder(
            new EmptyBorder(1, 1, 1, 1),
            new LineBorder(Color.LIGHT_GRAY, 1)
    );
    
    /**
     * Бордюр редактора, имеющего в данный момент фокус ввода.
     */
    Border BORDER_ACTIVE = new LineBorder(Color.decode("#3399FF"), 2);
    
    /**
     * Бордюр редактора, содержащего ошибочное значение.
     */
    Border BORDER_ERROR  = new LineBorder(Color.decode("#DE5347"), 2);
    
    /**
     * Текст заглушки, отображающегося если свойство не имеет значения.
     */
    String NOT_DEFINED = Language.get("error@novalue");
    
    /**
     * Получить метку, содержащую текст наименования свойства.
     */
    JLabel getLabel();
    
    /**
     * Получить виджет редактора.
     */
    Box getEditor();
    
    /**
     * Реализует конструирование виджета редактора.
     */
    Box createEditor();
    
    /**
     * Установка бордюра редактора.
     * @param border Новый бордюр.
     */
    void setBorder(Border border);

    /**
     * Получение значения редактора, отображаемое в GUI.
     */
    V getValue();
    
    /** 
     * Установка значения редактора, отображаемое в GUI.
     * @param value Экземпляр типа, соответсвующий внутреннему значению свойства.
     */
    void setValue(V value);
    
    /**
     * Переключение доступности редактирования значения пользователем.
     * @param editable Если TRUE - значение можно редактировать.
     */
    void setEditable(boolean editable);
    
    /**
     * Позволяет определить, возможно ли редактирование.
     */
    boolean isEditable();
    
    /**
     * Установить признак видимости редактора.
     * @param visible Если TRUE - редактор будет отбражаться.
     */
    void setVisible(boolean visible);
    
    /**
     * Возвращает признак видимости редактора.
     */
    boolean isVisible();
    
    /**
     * Добавить команду изменения свойства.
     * @param command Ссылка на команду свойства.
     */
    void addCommand(EditorCommand<T, V> command);
    
    /**
     * Получить текущий список назначенных редактору команд.
     */
    List<EditorCommand<T, V>> getCommands();
    
    /**
     * Возвращает компонент редактора, который может получить фокус ввода.
     */
    default Component getFocusTarget() {
        return null;
    }
    
    /**
     * Прекращает редактирование свойства и возвращает флаг корректности значения.
     */
    default boolean stopEditing() {
        return true;
    }

    default void setPlaceHolder(String text) {}
    
}
