package spacemgr.command.defragment;

import javax.swing.*;
import java.awt.*;

class CommandDialog extends JFrame {

    CommandDialog(IFormController controller) {
        super(Defragmentation.TITLE);
        setIconImage(Defragmentation.ICON_MAIN.getImage());
        getContentPane().add(controller.getFormView());
        pack();
        setLocationRelativeTo(null);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1200, super.getPreferredSize().height);
    }
}
