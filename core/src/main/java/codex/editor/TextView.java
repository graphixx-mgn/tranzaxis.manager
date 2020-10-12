package codex.editor;

import codex.property.PropertyHolder;
import codex.type.AnyType;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Редактор свойств типа {@link codex.type.Str}, представляет собой нередактируемое текстовое поле
 * для отображения длинных строк.
 */
@ThreadSafe
public class TextView extends AbstractEditor<AnyType, Object> {

    private JTextPane pane;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public TextView(PropertyHolder<AnyType, Object> propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        Font font = new Font("SansSerif", Font.PLAIN, (int) (UIManager.getDefaults().getFont("Label.font").getSize() * 1.1));

        pane = new JTextPane() {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
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
    protected void updateValue(Object value) {
        pane.setText(value == null ? "" : value.toString());
    }
}
