package manager.utils;

import codex.command.EditorCommand;
import codex.component.dialog.Dialog;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.UpgradeUnit;
import manager.xml.Change;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Versioning {

    private final static ImageIcon ICON_INFO = ImageUtils.getByPath("/images/info.png");
    
    private static String getChangesHtml(List<Version> changes) {
        return MessageFormat.format(
                Language.get(UpgradeUnit.class, "html@changes"),
                changes.stream()
                        .map(Versioning::getVersionTable)
                        .collect(Collectors.joining())
        );
    }

    private static String getVersionTable(Version version) {
        Map<Change.Scope.Enum, List<Change>> changeMap = Arrays.stream(version.getChangelog().getChangeArray())
                .collect(Collectors.groupingBy(Change::getScope));
        return MessageFormat.format(
                Language.get(UpgradeUnit.class, "html@version.table"),
                MessageFormat.format(
                        Language.get(UpgradeUnit.class, "html@version.title"),
                        version.getNumber(), version.getDate()
                ),
                changeMap.entrySet().stream()
                        .map(Versioning::getScopeRows)
                        .collect(Collectors.joining())
        );
    }

    private static String getScopeRows(Map.Entry<Change.Scope.Enum, List<Change>> entry) {
        return entry.getValue().stream()
                .map(change -> MessageFormat.format(
                        entry.getValue().indexOf(change) == 0 ?
                                "<tr><td align='center' rowspan=''{0}''>{1}</td><td align='center' class=''{2}''><img src=''{4}'' width=22 height=22></td><td class=''{2}''><p style=''padding-right: 10px''>{3}</p></td></tr>" :
                                "<tr><td align='center' class=''{2}''><img src=''{4}'' width=22 height=22></td><td class=''{2}''><p style=''padding-right: 10px''>{3}</p></td></tr>",
                        entry.getValue().size(),
                        Language.get(UpgradeUnit.class, "html@scope."+change.getScope()),
                        change.getType(),
                        change.getDescription().replaceAll("\\n", "<br>"),
                        Versioning.class.getResource(Language.get(UpgradeUnit.class, "html@type."+change.getType().toString()))
                ))
                .collect(Collectors.joining());
    }

    private static void showVersionInfo(List<Version> versions, String title) {
        JTextPane infoPane = new JTextPane() {
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
        infoPane.setEditable(false);
        infoPane.setContentType("text/html");
        infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        infoPane.setText(Versioning.getChangesHtml(versions));

        JScrollPane scrollPane = new JScrollPane() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(
                        800,
                        Math.min(
                                super.getPreferredSize().height,
                                400
                        )
                );
            }
        };
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(infoPane);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new LineBorder(Color.GRAY, 1)
        ));

        JPanel content = new JPanel(new BorderLayout());
        content.add(scrollPane, BorderLayout.CENTER);

        new codex.component.dialog.Dialog(
                null,
                ICON_INFO,
                title,
                content,
                null,
                Dialog.Default.BTN_CLOSE.newInstance()
        ).setVisible(true);
    }

    public static class ShowChanges extends EditorCommand<AnyType, Object> {

        private final VersionsDocument versions;
        private final String dialogTitle;

        public ShowChanges(VersionsDocument versions) {
            this(versions, null);
        }

        public ShowChanges(VersionsDocument versions, String dialogTitle) {
            super(
                    ICON_INFO,
                    Language.get(Versioning.class,"history@command"),
                    holder -> versions != null
            );
            this.versions = versions;
            this.dialogTitle = IComplexType.coalesce(
                    dialogTitle,
                    Language.get(Versioning.class,"history@dialog")
            );
        }

        @Override
        public void execute(PropertyHolder<AnyType, Object> context) {
            showVersionInfo(Arrays.asList(versions.getVersions().getVersionArray()), dialogTitle);
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }
    }
}
