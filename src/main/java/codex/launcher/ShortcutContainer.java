package codex.launcher;

import codex.component.layout.WrapLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.JPanel;

class ShortcutContainer extends JPanel {

    private final ShortcutSection section;
    
    ShortcutContainer(ShortcutSection section) {
        this.section = section;
    }
    
    {
        setLayout(new WrapLayout(FlowLayout.LEFT));
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
    
    ShortcutSection getSection() {
        return section;
    }
    
}
