package codex.unit;

import javax.swing.JComponent;

public abstract class AbstractUnit {
    
    protected final JComponent view = createViewport();
    
    public abstract JComponent createViewport();
    public void viewportBound() {};
    
    public final JComponent getViewport() {
        return view;
    };
    
}
