package plugin;

public final class PluginException extends Exception {

    private final Boolean handled;

    public PluginException(String message) {
        this(message, false);
    }

    public PluginException(String message, boolean handled) {
        super(message);
        this.handled = handled;
    }

    final boolean isHandled() {
        return handled;
    }
}
