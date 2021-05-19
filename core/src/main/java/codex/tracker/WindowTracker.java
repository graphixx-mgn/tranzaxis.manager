package codex.tracker;

import codex.model.Access;
import codex.service.AbstractService;
import codex.type.Int;
import codex.type.Str;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class WindowTracker extends AbstractService<WindowTrackerOptions> implements IWindowTracker {

    private final static String  DIM_POS_X  = "x_pos";
    private final static String  DIM_POS_Y  = "y_pos";
    private final static String  DIM_WIDTH  = "width";
    private final static String  DIM_HEIGHT = "height";
    private final static String  DIM_STATE1 = "state";
    private final static String  DIM_STATE2 = "state.ext";

    private final static Integer UPDATE_DELAY = 500;

    private final Queue<ComponentEvent> eventQueue = new PriorityQueue<>(Comparator.comparingInt(AWTEvent::getID).reversed());

    @Override
    public void registerWindow(JFrame window, String classifier) {
        if (!getSettings().model.hasProperty(classifier))
            window.addComponentListener(new Tracker(window, classifier));
    }

    @Override
    public void startService() {

    }


    private class Tracker extends ComponentAdapter implements ActionListener, WindowStateListener {

        private final JFrame window;
        private final String classifier;
        private final Timer  timer = new Timer(UPDATE_DELAY, this) {{
            setRepeats(false);
        }};
        private final Map<String, Integer> dimensions = new LinkedHashMap<>();

        Tracker(JFrame window, String classifier) {
            this.window = window;
            this.classifier = classifier;
            getSettings().model.addUserProp(
                    classifier,
                    new codex.type.Map<>(new Str(), new Int(), dimensions),
                    false, Access.Any
            );
            restoreWindowState();
            window.addWindowStateListener(this);
        }

        @Override
        public void componentResized(ComponentEvent event) {
            processEvent(event);
        }

        @Override
        public void componentMoved(ComponentEvent event) {
            processEvent(event);
        }

        @Override
        public void windowStateChanged(WindowEvent event) {
            processEvent(event);
        }

        private void processEvent(ComponentEvent event) {
            synchronized (eventQueue) {
                eventQueue.add(event);
            }
            timer.restart();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            synchronized (eventQueue) {
                if (!eventQueue.isEmpty()) {
                    ComponentEvent event  = eventQueue.poll();
                    if (event.getID() == WindowEvent.WINDOW_STATE_CHANGED) {
                        // Не обновляем состояние если окно свернуто (1)
                        if (window.getExtendedState() != Frame.ICONIFIED) {
                            updateStates();
                        }
                    } else {
                        // Не обновляем размеры если окно свернуто (1) или развернуто (6)
                        if (window.getExtendedState() == Frame.NORMAL) {
                            updateDimensions();
                        }
                    }
                    saveWindowState();
                    eventQueue.clear();
                }
            }
        }

        private void saveWindowState() {
            getSettings().model.setValue(classifier, dimensions);
            try {
                getSettings().model.commit(false, classifier);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Integer> getSavedState() {
            return (Map<String, Integer>) getSettings().model.getValue(classifier);
        }

        private void restoreWindowState() {
            dimensions.putAll(getSavedState());
            if (dimensions.isEmpty()) return;

            if (dimensions.containsKey(DIM_WIDTH) && dimensions.containsKey(DIM_HEIGHT)) {
                window.setSize(dimensions.get(DIM_WIDTH), dimensions.get(DIM_HEIGHT));
            }
            if (dimensions.containsKey(DIM_POS_X) && dimensions.containsKey(DIM_POS_Y)) {
                window.setLocation(dimensions.get(DIM_POS_X), dimensions.get(DIM_POS_Y));
            }
            if (dimensions.containsKey(DIM_STATE1) && dimensions.containsKey(DIM_STATE2)) {
                window.setState(dimensions.get(DIM_STATE1));
                window.setExtendedState(dimensions.get(DIM_STATE2));
            }
        }

        private void updateDimensions() {
            Rectangle bounds = window.getBounds();
            dimensions.put(DIM_POS_X,  bounds.x);
            dimensions.put(DIM_POS_Y,  bounds.y);
            dimensions.put(DIM_WIDTH,  bounds.width);
            dimensions.put(DIM_HEIGHT, bounds.height);
        }

        private void updateStates() {
            dimensions.put(DIM_STATE1, window.getState());
            dimensions.put(DIM_STATE2, window.getExtendedState());
        }
    }
}