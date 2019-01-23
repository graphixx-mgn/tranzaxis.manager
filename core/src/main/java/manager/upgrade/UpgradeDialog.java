package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import manager.xml.Change;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import sun.swing.SwingUtilities2;

class UpgradeDialog extends Dialog {

    private final JLabel upgradeInfo;
    private final JTextPane infoPane;
    private final Document infoDocument;

    UpgradeDialog(ActionListener closeAction) {
        super(
                FocusManager.getCurrentManager().getActiveWindow(),
                ImageUtils.getByPath("/images/upgrade.png"),
                Language.get(UpgradeUnit.class.getSimpleName(), "info@title"),
                new JPanel(),
                closeAction,
                closeAction == null ? new DialogButton[] {
                        Dialog.Default.BTN_CANCEL.newInstance()
                } : new DialogButton[] {
                        Dialog.Default.BTN_OK.newInstance(Language.get(UpgradeUnit.class.getSimpleName(), "info@start")),
                        Dialog.Default.BTN_CANCEL.newInstance()
                }
        );
        setResizable(false);

        upgradeInfo = new JLabel(
                ImageUtils.resize(ImageUtils.getByPath("/images/remotehost.png"), 24, 24),
                SwingConstants.LEFT
        );
        upgradeInfo.setIconTextGap(6);
        upgradeInfo.setVisible(closeAction != null);
        upgradeInfo.setBorder(new EmptyBorder(5, 5, 5, 5));

        infoPane = new JTextPane() {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
        infoDocument = infoPane.getDocument();
        infoPane.setEditable(false);
        infoPane.setPreferredSize(new Dimension(700, 300));
        infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);


        Style defStyle  = infoPane.addStyle(Change.Type.CHANGE.toString(),  null);
        Style addStyle  = infoPane.addStyle(Change.Type.FEATURE.toString(), defStyle);
        Style fixStyle  = infoPane.addStyle(Change.Type.BUGFIX.toString(),  defStyle);
        Style headStyle = infoPane.addStyle("head", null);

        StyleConstants.setFontSize(headStyle, 14);
        StyleConstants.setUnderline(headStyle, true);
        StyleConstants.setFontFamily(headStyle, "Arial Black");
        StyleConstants.setForeground(addStyle, Color.decode("#00822C"));
        StyleConstants.setForeground(fixStyle, Color.decode("#FF3333"));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(infoPane);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new LineBorder(Color.GRAY, 1)
        ));

        JPanel content = new JPanel(new BorderLayout());
        content.add(upgradeInfo, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        setContent(content);
    }

    void updateInfo(VersionsDocument changes, int providers) {
        upgradeInfo.setText(MessageFormat.format(
                Language.get(UpgradeUnit.class.getSimpleName(), "available"),
                changes.getVersions().getCurrent(),
                providers
        ));
        infoPane.setText(null);
        try {
            for (Version version : changes.getVersions().getVersionArray()) {
                infoDocument.insertString(
                        infoDocument.getLength(),
                        MessageFormat.format(
                                Language.get(UpgradeUnit.class.getSimpleName(), "info@next"),
                                version.getNumber(), version.getDate()
                        ).concat("\n"), infoPane.getStyle("head")
                );
                for (Change change : version.getChangelog().getChangeArray()) {
                    infoDocument.insertString(
                            infoDocument.getLength(),
                            MessageFormat.format(
                                    "\u2022 [{0}] {1}\n",
                                    String.format("%4s", change.getScope()),
                                    change.getDescription().trim().replaceAll("\\n\\s*", " ")
                            ),
                            infoPane.getStyle(change.getType().toString())
                    );
                }
                infoDocument.insertString(infoDocument.getLength(), "\n", infoPane.getStyle(Change.Type.CHANGE.toString()));
            }
        } catch (BadLocationException e1) {
        }
    }

}
