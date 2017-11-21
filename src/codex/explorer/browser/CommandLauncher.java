package codex.explorer.browser;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.utils.ImageUtils;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;

/**
 * Ярлык запуска команды с панели быстрого доступа {@link Launcher}.
 */
final class CommandLauncher extends LaunchButton {
    
    private final JLabel signDelete;
    
    /**
     * Конструктор ярлыка.
     * @param entity Ссылка на сущность.
     * @param command Ссылка на команду, доступную для класса сущности.
     */
    CommandLauncher(Entity entity, EntityCommand command, String title) {
        super(title, command.getButton().getIcon());
        setLayout(null);
        
        signDelete = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/clearval.png"), 18, 18));
        signDelete.setVisible(false);
        add(signDelete);
        
        
        Insets insets = signDelete.getInsets();
        Dimension size = signDelete.getPreferredSize();
        signDelete.setBounds(
                getPreferredSize().width - size.width - 3, 
                insets.top + 3,
                size.width, 
                size.height
        );
        
        addActionListener((event) -> {
            command.execute(entity);
        });
        
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                getModel().setRollover(true);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                //TODO: delete from DB
                System.err.println("delete launcher");
            }
        });
    }
    
    @Override
    public void stateChanged(ChangeEvent event) {
        super.stateChanged(event);
        signDelete.setVisible(getModel().isRollover());
    }
    
}
