package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.instance.Instance;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import javax.swing.FocusManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.ScrollPaneLayout;
import javax.swing.SwingConstants;
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

public class UpgradeDialog extends Dialog {
    
    public UpgradeDialog(Instance instance, VersionsDocument diff, ActionListener closeAction) {
        super(
            FocusManager.getCurrentManager().getActiveWindow(), 
            ImageUtils.getByPath("/images/upgrade.png"), 
            Language.get(UpgradeUnit.class.getSimpleName(), "info@title"), 
            new JPanel(new BorderLayout()) {{
                JLabel label = new JLabel(
                        MessageFormat.format(
                                Language.get(UpgradeUnit.class.getSimpleName(), "available"), 
                                diff.getVersions().getCurrent()
                        ),
                        ImageUtils.resize(ImageUtils.getByPath("/images/info.png"), 24, 24),
                        SwingConstants.LEFT
                );
                label.setBorder(new EmptyBorder(5, 5, 5, 5));
                add(label, BorderLayout.NORTH);
                
                JTextPane infoPane = new JTextPane();
                Document infoDocument = infoPane.getDocument();
                infoPane.setEditable(false);
                infoPane.setPreferredSize(new Dimension(700, 300));
                infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

                Style defStyle  = infoPane.addStyle(Change.Type.CHANGE.toString(),  null);
                Style addStyle  = infoPane.addStyle(Change.Type.FEATURE.toString(), null);
                Style fixStyle  = infoPane.addStyle(Change.Type.BUGFIX.toString(),  null);
                Style headStyle = infoPane.addStyle("head", null);

                StyleConstants.setFontSize(headStyle, 14);
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
                add(scrollPane, BorderLayout.CENTER);
                
                infoPane.setText(null);
                try {
                    for (Version version : diff.getVersions().getVersionArray()) {
                        infoDocument.insertString(
                                infoDocument.getLength(),
                                MessageFormat.format(
                                        Language.get(UpgradeUnit.class.getSimpleName(), "info@next"), 
                                        version.getNumber(), version.getDate()
                                ).concat("\n"), headStyle
                        );
                        for (Change change : version.getChangelog().getChangeArray()) {
                            infoDocument.insertString(
                                    infoDocument.getLength(), 
                                    MessageFormat.format(
                                            "* [{0}] {1}\n\n", 
                                            change.getScope(), 
                                            change.getDescription().trim()
                                    ),
                                    infoPane.getStyle(change.getType().toString())
                            );
                        }
                        infoDocument.insertString(infoDocument.getLength(), "\n", defStyle);
                    }
                } catch (BadLocationException e1) {}
            }}, 
            closeAction,
            Dialog.Default.BTN_OK.newInstance(Language.get(UpgradeUnit.class.getSimpleName(), "info@start")),
            Dialog.Default.BTN_CANCEL.newInstance()
        );
    }

}
