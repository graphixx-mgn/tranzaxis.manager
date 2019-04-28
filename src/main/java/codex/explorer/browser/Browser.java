package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import javax.swing.*;

/**
 * Панель просмотра проводника. Отображает презентации редактора и селектора
 * активного узла дерева.
 */
public final class Browser extends JPanel {

    static {
        UIManager.put ("TabbedPane.contentBorderInsets", new Insets (-1, 2, 0, 2));
        UIManager.put("TabbedPane.tabInsets", new Insets(2, 4, 2, 6));
    }

    private enum TabKind {

        Selector(ImageUtils.resize(ImageUtils.getByPath("/images/selector.png"), 20,20), Language.get(Browser.class, "tab@selector")),
        Editor(ImageUtils.resize(ImageUtils.getByPath("/images/general.png"), 20,20), Language.get(Browser.class, "tab@general"));

        private final ImageIcon icon;
        private final String    title;
        TabKind(ImageIcon icon, String title) {
            this.icon  = icon;
            this.title = title;
        }
    }

    private final JTabbedPane tabPanel = new JTabbedPane();
    
    /**
     * Конструктор панели.
     */
    public Browser() {
        super(new BorderLayout());
        add(tabPanel);
    }
    
    /**
     * Загружает указанный узел в панель просмотра.
     * @param node Ссылка на узел.
     */
    public void browse(INode node) {
        tabPanel.removeAll();

        SelectorPresentation selectorPresentation = node.getSelectorPresentation();
        if (selectorPresentation != null) {
            selectorPresentation.refresh();
            tabPanel.insertTab(
                    TabKind.Selector.title,
                    TabKind.Selector.icon,
                    selectorPresentation,
                    null, tabPanel.getTabCount()
            );
        }
        EditorPresentation editorPresentation = node.getEditorPresentation();
        if (editorPresentation != null) {
            editorPresentation.refresh();
            tabPanel.insertTab(
                    TabKind.Editor.title,
                    TabKind.Editor.icon,
                    new JPanel(new BorderLayout()) {{
                        add(editorPresentation, BorderLayout.NORTH);
                    }},
                    null, tabPanel.getTabCount()
            );
        }
    }
    
}
