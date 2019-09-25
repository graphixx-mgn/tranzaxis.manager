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

    private final BrowseMode mode;
    
    /**
     * Конструктор панели.
     */
    public Browser(BrowseMode mode) {
        super(new BorderLayout());
        this.mode = mode;
        add(this.mode.getView());
    }
    
    /**
     * Загружает указанный узел в панель просмотра.
     * @param node Ссылка на узел.
     */
    public void browse(INode node) {
        mode.browse(node);
    }
    
}
