package codex.notification;

public enum Handler {
    Tray(TrayInformer.getInstance()),
    Inbox(MessageInbox.getInstance());

    private final IMessageHandler handler;

    Handler(IMessageHandler handler) {
        this.handler = handler;
    }

    IMessageHandler getHandler() {
        return handler;
    }
}
