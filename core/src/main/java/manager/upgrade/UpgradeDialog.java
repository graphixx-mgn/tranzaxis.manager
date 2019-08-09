package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;
import manager.xml.VersionsDocument;
import utils.Versioning;


class UpgradeDialog extends Dialog {

    private final JLabel upgradeInfo;
    private final JTextPane infoPane;

    UpgradeDialog(ActionListener closeAction) {
        super(
                FocusManager.getCurrentManager().getActiveWindow(),
                ImageUtils.getByPath("/images/upgrade.png"),
                Language.get(UpgradeUnit.class, "info@title"),
                new JPanel(),
                closeAction,
                closeAction == null ? new DialogButton[] {
                        Dialog.Default.BTN_CANCEL.newInstance()
                } : new DialogButton[] {
                        Dialog.Default.BTN_OK.newInstance(Language.get(UpgradeUnit.class, "info@start")),
                        Dialog.Default.BTN_CANCEL.newInstance()
                }
        );
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
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
        infoPane.setContentType("text/html");
        infoPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        infoPane.setEditable(false);
        infoPane.setPreferredSize(new Dimension(800, 400));
        infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

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
                Language.get(UpgradeUnit.class, "available"),
                changes.getVersions().getCurrent(),
                providers
        ));
        infoPane.setText(Versioning.getChangesHtml(Arrays.asList(changes.getVersions().getVersionArray())));
    }

}
