package codex.tracker;

import codex.service.Service;
import codex.type.EntityRef;

public class WindowTrackerOptions extends Service<WindowTracker> {

    public WindowTrackerOptions(EntityRef owner, String title) {
        super(owner, title);
    }
}
