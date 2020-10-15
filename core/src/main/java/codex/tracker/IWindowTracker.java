package codex.tracker;

import codex.service.IService;
import javax.swing.*;

public interface IWindowTracker extends IService {
    @Override
    default String getTitle() {
        return "Window position tracker";
    }

    void registerWindow(JFrame window, String classifier);
}
