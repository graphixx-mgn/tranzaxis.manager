package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.type.IComplexType;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

@ThreadSafe
public final class EmbeddedMode extends BrowseMode<JPanel> {

    private final JPanel editorPanel;
    private final JPanel selectorPanel;

    public EmbeddedMode() {
        super(JPanel.class);
        container.setLayout(new BorderLayout());

        editorPanel   = new JPanel(new BorderLayout());
        selectorPanel = new JPanel(new BorderLayout());

        container.add(editorPanel,   BorderLayout.NORTH);
        container.add(selectorPanel, BorderLayout.CENTER);
    }

    @Override
    void browse(INode node) {
        editorPanel.removeAll();
        selectorPanel.removeAll();

        EditorPresentation editorPresentation = node.getEditorPresentation();
        if (editorPresentation != null) {
            editorPanel.add(editorPresentation);
            editorPresentation.refresh();
        }
        reloadEmbeddedSelector(node);
        node.addNodeListener(new INodeListener() {
            @Override
            public void childInserted(INode parentNode, INode childNode) {
                reloadEmbeddedSelector(node);
            }
            @Override
            public void childDeleted(INode parentNode, INode childNode, int index) {
                reloadEmbeddedSelector(node);
            }
            @Override
            public void childMoved(INode parentNode, INode childNode, int from, int to) {
                reloadEmbeddedSelector(node);
            }
            @Override
            public void childReplaced(INode prevChild, INode nextChild) {
                reloadEmbeddedSelector(node);
            }
            @Override
            public void childChanged(INode childNode) {
                reloadEmbeddedSelector(node);
            }
        });
        editorPanel.revalidate();
        editorPanel.repaint();
        selectorPanel.revalidate();
        selectorPanel.repaint();
    }

    private void reloadEmbeddedSelector(INode node) {
        SelectorPresentation embeddedSelector = node.getSelectorPresentation();
        if (embeddedSelector != null) {
            if (!((Entity) node).model.getProperties(Access.Edit).isEmpty()) {
                embeddedSelector.setBorder(new CompoundBorder(
                        new EmptyBorder(0, 5, 3, 5),
                        new TitledBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1),
                                IComplexType.coalesce(getDescription(getClassHierarchy(node), "group@title"), SELECTOR_TITLE)
                        )
                ));
            }
            if (selectorPanel.getComponentCount() == 0) {
                selectorPanel.add(embeddedSelector);
            } else {
                embeddedSelector.refresh();
            }
        }
    }
}
