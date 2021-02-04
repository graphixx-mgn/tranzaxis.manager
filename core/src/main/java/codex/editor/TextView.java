package codex.editor;

import codex.command.EditorCommand;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Редактор свойств типа {@link codex.type.Str}, представляет собой нередактируемое текстовое поле
 * для отображения длинных строк.
 */
@ThreadSafe
public class TextView extends AbstractEditor<AnyType, Object> {

    private JTextPane textPanel;
    private JPanel commandPanel;

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
        textPanel = new JTextPane() {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
        textPanel.setForeground(IEditor.COLOR_DISABLED);
        textPanel.setBackground(Color.decode("#F5F5F5"));
        textPanel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        textPanel.setContentType("text/html");

        textPanel.setFont(font);
        textPanel.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(textPanel) {
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, textPanel.getFont().getSize()*10);
            }
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(textPanel.getPreferredScrollableViewportSize().width, textPanel.getFont().getSize()*10);
            }
        };
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        scrollPane.setColumnHeader(null);
        scrollPane.setAlignmentY(TOP_ALIGNMENT);

        commandPanel = new JPanel();
        commandPanel.setLayout(new BoxLayout(commandPanel, BoxLayout.Y_AXIS));
        commandPanel.setAlignmentY(TOP_ALIGNMENT);

        Box container = new Box(BoxLayout.X_AXIS) {
            @Override
            public void setBorder(Border border) {
                scrollPane.setBorder(border);
            }
        };
        container.add(scrollPane);
        container.add(commandPanel);
        return container;
    }

    @Override
    public void addCommand(EditorCommand<AnyType, Object> command) {
        final EditorCommandButton button = new EditorCommandButton(command);
        commands.add(command);
        commandPanel.add(button);
        command.setContext(propHolder);
    }

    @Override
    protected void updateValue(Object value) {
        textPanel.setText(value == null ? "" : value.toString());
    }
}
