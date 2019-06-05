package codex.editor;

import codex.property.PropertyHolder;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Редактор свойств типа {@link codex.type.Str}, представляет собой нередактируемое текстовое поле
 * для отображения длинных строк.
 */
public class TextView extends AbstractEditor {

    private JTextPane pane;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public TextView(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        Font font = new Font("SansSerif", Font.PLAIN, (int) (UIManager.getDefaults().getFont("Label.font").getSize() * 1.1));

        pane = new JTextPane();
        pane.setForeground(IEditor.COLOR_DISABLED);
        pane.setBackground(Color.decode("#F5F5F5"));
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setContentType("text/html");

        pane.setFont(font);
        pane.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(pane) {
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, pane.getFont().getSize()*10);
            }
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(pane.getPreferredScrollableViewportSize().width, pane.getFont().getSize()*10);
            }
        };
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        scrollPane.setColumnHeader(null);

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBorder(new LineBorder(Color.RED));
        container.add(scrollPane);
        return container;
    }

    @Override
    public void setValue(Object value) {
        if (value == null) {
            pane.setText("");
        } else {
            pane.setText(value.toString());
        }
    }

}
