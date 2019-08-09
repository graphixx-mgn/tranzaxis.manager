package manager.utils;

import codex.utils.Language;
import manager.upgrade.UpgradeUnit;
import manager.xml.Change;
import manager.xml.Version;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Versioning {
    public static String getChangesHtml(List<Version> changes) {
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
                                "<tr><td align='center' rowspan=''{0}''>{1}</td><td align='center' class=''{2}''><img src=''{4}'' width=22 height=22></td><td class=''{2}''>{3}</td></tr>" :
                                "<tr><td align='center' class=''{2}''><img src=''{4}'' width=22 height=22></td><td class=''{2}''>{3}</td></tr>",
                        entry.getValue().size(),
                        Language.get(UpgradeUnit.class, "html@scope."+change.getScope()),
                        change.getType(),
                        change.getDescription(),
                        Versioning.class.getResource(Language.get(UpgradeUnit.class, "html@type."+change.getType().toString()))
                ))
                .collect(Collectors.joining());
    }
}
