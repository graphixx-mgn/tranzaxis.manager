package codex.task;

import codex.component.panel.HTMLView;
import codex.log.Level;
import codex.utils.ImageUtils;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class TaskOutput extends JPanel {

    private static final ThreadLocal<ITask>     CONTEXT = new InheritableThreadLocal<>();
    private static final Map<ITask, TaskOutput> OUTPUT_MAP = new HashMap<>();
    private static final Map<Level, Color>      COLOR_MAP = new HashMap<Level, Color>(){{
        put(Level.Debug, Color.GRAY);
        put(Level.Info,  Color.BLACK);
        put(Level.Warn,  Color.decode("#AA3333"));
        put(Level.Error, Color.decode("#FF3333"));
    }};

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
        if (output == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String colorCode = ImageUtils.hexColor(COLOR_MAP.get(level));

            HTMLEditorKit kit = (HTMLEditorKit) output.pane.getEditorKit();
            HTMLDocument  doc = (HTMLDocument) output.pane.getDocument();
            try {
                kit.insertHTML(
                        doc,
                        doc.getLength(),
                        MessageFormat.format(
                                "<span><font color=\"{0}\">{1}</font></span>{2}",
                                colorCode,
                                MessageFormat.format(
                                        message.replaceAll(" ", "&nbsp;").replace("\r\n", "<br>"),
                                        params
                                ),
                                doc.getLength() > 0 ? "<br>" : ""
                        ),
                        0, 0, null
                );
            } catch (BadLocationException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    synchronized static void defineContext(ITask task) {
        CONTEXT.set(task);
    }

    synchronized static void clearContext() {
        CONTEXT.set(null);
    }

    private final HTMLView pane = new HTMLView() {{
        setDocument(new HTMLDocument());
        setOpaque(true);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        setBackground(UIManager.getDefaults().getColor("TextPane.background"));
        ((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(
                                new StringSelection(
                                        pane.getSelectedText().replaceAll("\\u00a0"," ")
                                        ),
                                null
                        );
            }
        });
    }};

    private TaskOutput() {
        super(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(pane);
        scrollPane.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        add(scrollPane, BorderLayout.CENTER);
    }
}
