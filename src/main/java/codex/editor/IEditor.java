package codex.editor;

import codex.command.EditorCommand;
import codex.property.PropertyHolder;
import codex.utils.Language;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Интерфейс GUI редакторов свойств {@link PropertyHolder}
 * @param <T> Тип внутреннего значения свойства.
 */
public interface IEditor<T> {
    
    /**
     * Стандартный цвет шрифта для отрисовки значения свойств.
     */
    public static final Color COLOR_NORMAL = UIManager.getDefaults().getColor("Label.foreground");
    
    /**
     * Стандартный цвет шрифта для отрисовки не редактиремых свойств.
     */
    public static final Color COLOR_INACTIVE = Color.DARK_GRAY;
    
    /**
     * Цвет шрифта для отрисовки значения заблокированных свойств.
     */
    public static final Color COLOR_DISABLED = Color.GRAY;
    
    /**
     * Цвет шрифта для отрисовки значения свойств с ошибочным значением.
     */
    public static final Color COLOR_INVALID = Color.RED;
    
    /**
     * Стандартный шрифт наименований свойств.
     */
    public static final Font FONT_NORMAL = UIManager.getDefaults().getFont("Label.font");
    
    /**
     * Шрифт наименования обязательного свойства с пустым значением.
     * @see PropertyHolder#isValid()
     */
    public static final Font FONT_BOLD   = FONT_NORMAL.deriveFont(Font.BOLD);
    
    /**
     * Стандартный шрифт отображения значений свойств.
     */
    public static final Font FONT_VALUE = new Font("SansSerif", Font.PLAIN, (int) (UIManager.getDefaults().getFont("Label.font").getSize() * 1.3));
    
    /**
     * Стандартный бордюр редактора.
     */
    public static final Border BORDER_NORMAL = new CompoundBorder(
            new EmptyBorder(1, 1, 1, 1),
            new LineBorder(Color.LIGHT_GRAY, 1)
    );
    
    /**
     * Бордюр редактора, имеющего в данный момент фокус ввода.
     */
    public static final Border BORDER_ACTIVE = new LineBorder(Color.decode("#3399FF"), 2);
    
    /**
     * Бордюр редактора, содержащего ошибочное значение.
     */
    public static final Border BORDER_ERROR  = new LineBorder(Color.decode("#DE5347"), 2);
    
    /**
     * Текст заглушки, отображающегося если свойство не имеет значения.
     */
    public static final String NOT_DEFINED = Language.get("error@novalue");
    
    /**
     * Получить метку, содержащую текст наименования свойства.
     */
    public JLabel getLabel();
    
    /**
     * Получить виджет редактора.
     */
    public Box getEditor();
    
    /**
     * Реализует конструирование виджета редактора.
     */
    public Box createEditor();
    
    /**
     * Установка бордюра редактора.
     * @param border Новый бордюр.
     */
    public void setBorder(Border border);
    
    /** 
     * Установка значения редактора, отображаемое в GUI.
     * @param value Экземпляр типа, соответсвующий внутреннему значению свойства.
     */
    public void setValue(T value);
    
    /**
     * Переключение доступности редактирования значения пользователем.
     * @param editable Если TRUE - значение можно редактировать.
     */
    public void setEditable(boolean editable);
    
    /**
     * Позволяет определить, возможно ли редактирование.
     */
    public boolean isEditable();
    
    /**
     * Установить признак видимости редактора.
     * @param visible Если TRUE - редактор будет отбражаться.
     */
    public void setVisible(boolean visible);
    
    /**
     * Возвращает признак видимости редактора.
     */
    public boolean isVisible();
    
    /**
     * Добавить команду изменения свойства.
     * @param command Ссылка на команду свойства.
     */
    public void addCommand(EditorCommand command);
    
    /**
     * Получить текущий список назначенных редактору команд.
     */
    public List<EditorCommand> getCommands();
    
    /**
     * Возвращает компонент редактора, который может получить фокус ввода.
     */
    default public Component getFocusTarget() {
        return null;
    }
    
    /**
     * Прекращает редактирование свойства и возвращает флаг корректности значения.
     */
    default public boolean stopEditing() {
        return true;
    }
    
}
