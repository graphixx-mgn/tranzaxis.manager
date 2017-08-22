package codex.explorer.browser;

import codex.explorer.tree.AbstractNode;

public class Browser {
    
    public void browse(AbstractNode node) {
        System.out.println(node);
        System.out.println(node.getEditorPresentation());
        System.out.println(node.getSelectorPresentation());
    }
    
}
