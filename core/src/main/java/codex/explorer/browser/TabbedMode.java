package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.awt.*;


public final class TabbedMode extends BrowseMode<JTabbedPane> {

    static {
        UIManager.put ("TabbedPane.contentBorderInsets", new Insets (-1, 2, 0, 2));
        UIManager.put("TabbedPane.tabInsets", new Insets(2, 4, 2, 6));
    }

    public TabbedMode() {
        super(JTabbedPane.class);
    }

    @Override
    void browse(INode node) {
        container.removeAll();

        SelectorPresentation selectorPresentation = node.getSelectorPresentation();
        if (selectorPresentation != null) {
            selectorPresentation.refresh();
            container.insertTab(
                    IComplexType.coalesce(getDescription(getClassHierarchy(node), "group@title"), SELECTOR_TITLE),
                    TabKind.Selector.icon,
                    selectorPresentation,
                    null, container.getTabCount()
            );
        }
        EditorPresentation editorPresentation = node.getEditorPresentation();
        if (editorPresentation != null) {
            editorPresentation.refresh();
            container.insertTab(
                    EDITOR_TITLE,
                    TabKind.Editor.icon,
                    new JPanel(new BorderLayout()) {{
                        add(editorPresentation, BorderLayout.NORTH);
                    }},
                    null, container.getTabCount()
            );
        }
    }


    private enum TabKind {
        Selector(ImageUtils.resize(ImageUtils.getByPath("/images/selector.png"), 20,20)),
        Editor(ImageUtils.resize(ImageUtils.getByPath("/images/general.png"), 20,20));

        private final ImageIcon icon;
        TabKind(ImageIcon icon) {
            this.icon  = icon;
        }
    }

}
