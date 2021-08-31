package codex.editor;

import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

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
        textField.setEditable(false);
        textField.setBackground(null);

        iconLabel = new JLabel();
        iconLabel.setBorder(new EmptyBorder(2, 3, 1, 0));

        PlaceHolder placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField, PlaceHolder.Show.ALWAYS);
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(iconLabel);

        JPanel wrapper = new JPanel() {{
            setLayout(new GridBagLayout());
            add(textField, new GridBagConstraints() {{
                gridx = 0;
                gridy = 0;
                gridwidth = 1;
                gridheight = 1;
                anchor = GridBagConstraints.LINE_START;
                fill = GridBagConstraints.HORIZONTAL;
                weightx = 0.1;
                weighty = 1;
            }});
        }};
        container.add(wrapper);
        return container;
    }

    @Override
    protected void updateValue(Object value) {
        textField.setText(value == null ? "" : value.toString());
        ImageIcon icon;
        if (
                value != null &&
                Iconified.class.isAssignableFrom(value.getClass()) &&
                (icon = ((Iconified) value).getIcon()) != null
        ) {
            int aspectRatio = icon.getIconWidth() / icon.getIconHeight();
            iconLabel.setIcon(ImageUtils.resize(icon, 20 * aspectRatio, 20));
            iconLabel.setVisible(true);
        } else {
            iconLabel.setIcon(null);
            iconLabel.setVisible(false);
        }
    }
}
