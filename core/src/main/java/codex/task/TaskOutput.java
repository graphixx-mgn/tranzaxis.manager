package codex.task;

import codex.log.Level;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class TaskOutput extends JPanel {

    private static final ThreadLocal<ITask>     CONTEXT = new ThreadLocal<>();
    private static final Map<ITask, TaskOutput> OUTPUT_MAP = new HashMap<>();

    public static TaskOutput createOutput(ITask task) {
        if (!OUTPUT_MAP.containsKey(task)) {
            OUTPUT_MAP.put(task, new TaskOutput());
            task.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                    if (nextStatus.isFinal()) {
                        OUTPUT_MAP.remove(task);
                    }
                }
            });
        }
        return OUTPUT_MAP.get(task);
    }

    public static void put(Level level, String message, Object... params) {
        ITask context = CONTEXT.get();
        TaskOutput output = OUTPUT_MAP.get(context);
        if (output == null || !output.isShowing()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Style style = output.pane.getStyle(level.toString());
            Color color = StyleConstants.getForeground(style);
            String hex  = "#"+Integer.toHexString(color.getRGB()).substring(2);
            HTMLDocument doc = (HTMLDocument) output.pane.getStyledDocument();
            try {
                doc.insertAfterEnd(doc.getCharacterElement(
                        doc.getLength()),
                        MessageFormat.format(
                                "<span><font color=\"{0}\">{1}</font></span><br>",
                                hex,
                                MessageFormat.format(
                                        message.replaceAll(" ", "&nbsp;").replace("\r\n", "<br>"),
                                        params
                                )
                        )
                );
            } catch (BadLocationException | IOException e) {
                //
            }
        });
    }

    synchronized static void defineContext(ITask task) {
        CONTEXT.set(task);
    }

    synchronized static void clearContext() {
        CONTEXT.set(null);
    }

    private final JTextPane pane = new JTextPane() {{
        setEditable(false);
        setPreferredSize(new Dimension(450, 150));
        setContentType("text/html");
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        ((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
    }};

    private TaskOutput() {
        super(new BorderLayout());
        registerStyle(Level.Debug, Color.GRAY);
        registerStyle(Level.Info,  Color.BLACK);
        registerStyle(Level.Warn,  Color.decode("#AA3333"));
        registerStyle(Level.Error, Color.decode("#FF3333"));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(pane);
        scrollPane.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    private void registerStyle(Level level, Color color) {
        Style style = this.pane.addStyle(level.toString(),  null);
        style.addAttribute("level", level);
        StyleConstants.setForeground(style, color);
    }
}
