package codex.notification;

public interface IMessageChannel {

    String getTitle();
    void sendMessage(Message message);

}
