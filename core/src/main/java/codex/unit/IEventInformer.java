package codex.unit;

import java.util.List;

public interface IEventInformer {

    void addEventListener(IEventListener listener);
    void removeEventListener(IEventListener listener);
    List<IEventListener> getEventListeners();

    interface IEventListener {
        void showEvents(int events);
    }
}
