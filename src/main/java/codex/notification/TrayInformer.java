package codex.notification;

import codex.log.Logger;
import codex.service.ContextPresentation;
import codex.service.IContext;
import codex.service.ServiceCallContext;
import codex.service.ServiceRegistry;
import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.Map;

public class TrayInformer implements IMessageChannel {

    private final static TrayInformer INSTANCE = new TrayInformer();
    public  static TrayInformer getInstance() {
        return INSTANCE;
    }

    private TrayIcon trayIcon;
    private final AWTEventListener windowListener = (event) -> {
        if (event.getSource() instanceof JFrame) {
            JFrame frame = (JFrame) event.getSource();
            if (event.getID() == WindowEvent.WINDOW_OPENED && frame.getTitle() != null && frame.getIconImage() != null && !frame.isUndecorated()) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(this.windowListener);
                trayIcon = new TrayIcon(frame.getIconImage(), frame.getTitle());
                trayIcon.setImageAutoSize(true);
                try {
                    SystemTray.getSystemTray().add(trayIcon);
                } catch (AWTException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    TrayInformer() {
        if (SystemTray.isSupported()) {
            Toolkit.getDefaultToolkit().addAWTEventListener(windowListener, AWTEvent.WINDOW_EVENT_MASK);
        } else {
            Logger.getLogger().warn("NSS: System notification not supported by operating system");
        }
    }

    @Override
    public String getTitle() {
        return "System tray informer";
    }

    @Override
    public void sendMessage(Message message) {
        if (checkConditions() && trayIcon != null) {
            trayIcon.displayMessage(message.getHead(), message.getText(), message.getType());
        }
    }

    private boolean checkConditions() {
        INotificationService NSS = ServiceRegistry.getInstance().lookupService(INotificationService.class);
        Map<ContextPresentation, NotifyCondition> sources = NSS.getAccessor().getSources();
        return ServiceCallContext.getContext().stream()
                .map(IContext::getClassPresentation)
                .anyMatch(ctxObject ->
                        sources.containsKey(ctxObject) &&
                        sources.get(ctxObject).getCondition().get()
                );
    }
}
