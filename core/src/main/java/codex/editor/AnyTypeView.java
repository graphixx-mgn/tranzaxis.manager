package codex.editor;

import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link codex.type.AnyType}, представляет собой нередактируемое поле
 * в которе выводится строковое предстваление объекта {@link Object#toString()}, а если объект
 * реализует интерыейс {@link Iconified} - то и иконку.
 */
@ThreadSafe
public class AnyTypeView extends AbstractEditor<AnyType, Object> {

    private JTextPane textField;
    private JLabel    iconLabel;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public AnyTypeView(PropertyHolder<AnyType, Object> propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        textField = new JTextPane();
        textField.setFont(FONT_VALUE);
        textField.setContentType("text/html");
        textField.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        textField.setBorder(new EmptyBorder(2, 3, 3, 3));
        textField.setEditable(false);
        textField.setForeground(COLOR_DISABLED);
        textField.setOpaque(false);

        iconLabel = new JLabel();
        iconLabel.setBorder(new EmptyBorder(1, 3, 1, 3));

        PlaceHolder placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField, PlaceHolder.Show.ALWAYS);
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(iconLabel);
        container.add(textField);
        return container;
    }

    @Override
    protected void updateValue(Object value) {
        textField.setText(value == null ? "" : value.toString());
        if (value != null && Iconified.class.isAssignableFrom(value.getClass())) {
            iconLabel.setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 20, 20));
            iconLabel.setVisible(true);
        } else {
            iconLabel.setIcon(null);
            iconLabel.setVisible(false);
        }
    }
}
