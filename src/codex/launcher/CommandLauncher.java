package codex.launcher;

import codex.command.EntityCommand;
import static codex.launcher.LaunchButton.ERROR_BORDER;
import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import java.awt.AlphaComposite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;

/**
 * Ярлык запуска команды с панели быстрого доступа {@link LauncherUnit}.
 */
final class CommandLauncher extends LaunchButton {
    
    private final ImageIcon TILE = ImageUtils.getByPath("/images/strips_red.png");
    
    private final JLabel signDelete;
    private boolean invalid;
    
    /**
     * Конструктор ярлыка.
     * @param entity Ссылка на сущность.
     * @param command Ссылка на команду, доступную для класса сущности.
     */
    CommandLauncher(Entity entity, EntityCommand command, String title) {
        super(
                title, 
                command == null ? 
                    ImageUtils.getByPath("/images/warn.png") : 
                    command.getButton().getIcon()
        );
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
        setInvalid(entity == null);
        addActionListener((event) -> {
            if (!invalid) {
                Map<String, IComplexType> params = command.getParameters();
                if (params != null) {
                    Logger.getLogger().debug("Perform command [{0}]. Context: {1}", command.getName(), entity);
                    command.execute(entity, params);
                }
            }
        });
        
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                getModel().setRollover(true);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                Container panel = CommandLauncher.this.getParent();
                panel.remove(CommandLauncher.this);
                panel.revalidate();
                panel.repaint();
                
                Shortcut shortcut = new Shortcut(title);
                shortcut.model.remove();
            }
        });
    }
    
    public final void setInvalid(boolean invalid) {
        this.invalid = invalid;
        setBorder(invalid ? ERROR_BORDER : NORMAL_BORDER);
        repaint();
    }
    
    public final boolean isInvalid() {
        return this.invalid;
    }
    
    @Override
    public final void stateChanged(ChangeEvent event) {
        if (!invalid) {
            super.stateChanged(event);
        }
        signDelete.setVisible(getModel().isRollover());
    }
    
    @Override
    public final void paint(Graphics g) {
        super.paint(g);
        if (invalid) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
            int tileWidth = TILE.getIconWidth();
            int tileHeight = TILE.getIconHeight();
            Insets ins = getInsets();
            for (int y = 2; y < getHeight()-4; y += tileHeight) {
                for (int x = 2; x < getWidth()-4; x += tileWidth) {
                    g2d.drawImage(TILE.getImage(), x, y, this);
                }
            }
            g2d.dispose();
        }
    }
    
}
