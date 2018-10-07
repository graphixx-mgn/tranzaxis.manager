package codex.launcher;

import codex.component.panel.ScrollablePanel;
import java.awt.Component;
import javax.swing.BoxLayout;


final class SectionContainer extends ScrollablePanel {
    
    {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setScrollableWidth(ScrollablePanel.ScrollableSizeHint.FIT);
    }
    
    @Override
    public Component add(Component comp) {
        Component c = super.add(comp);
        revalidate();
        repaint();
        return c;
    }
    
    @Override
    public Component add(Component comp, int index) {
        Component c = super.add(comp, index);
        revalidate();
        repaint();
        return c;
    }

    @Override
    public void remove(Component comp) {
        super.remove(comp);
        revalidate();
        repaint();
    }
    
}
