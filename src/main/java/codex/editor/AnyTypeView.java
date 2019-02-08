package codex.editor;

import codex.property.PropertyHolder;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link codex.type.AnyType}, представляет собой нередактируемое поле
 * в которе выводится строковое предстваление объекта {@link Object#toString()}, а если объект
 * реализует интерыейс {@link Iconified} - то и иконку.
 */
public class AnyTypeView extends AbstractEditor {

    private JTextField textField;
    private JLabel     iconLabel;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public AnyTypeView(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);

        iconLabel = new JLabel();
        iconLabel.setBorder(new EmptyBorder(1, 2, 1, 3));

        PlaceHolder placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField, PlaceHolder.Show.ALWAYS);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(iconLabel);
        container.add(textField);
        return container;
    }

    @Override
    public final void setEditable(boolean editable) {
        super.setEditable(false);
    }

    @Override
    public void setValue(Object value) {
        textField.setText(value == null ? "" : value.toString());
        if (value != null && Iconified.class.isAssignableFrom(value.getClass())) {
            iconLabel.setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 18, 18));
            iconLabel.setVisible(true);
        } else {
            iconLabel.setIcon(null);
            iconLabel.setVisible(false);
        }
    }

}
