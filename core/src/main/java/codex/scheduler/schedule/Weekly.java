package codex.scheduler.schedule;

import codex.component.button.ToggleButton;
import codex.editor.AbstractEditor;
import codex.editor.IEditorFactory;
import codex.mask.DateFormat;
import codex.model.Access;
import codex.model.EntityDefinition;
import codex.property.PropertyHolder;
import codex.scheduler.Schedule;
import codex.type.Bool;
import codex.type.DateTime;
import codex.type.EntityRef;
import codex.type.Int;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.awt.*;
import java.text.DateFormatSymbols;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@EntityDefinition(title = "class@title", icon="/images/weekly.png")
public class Weekly extends Schedule {

    private final static int FIRST_WEEK_DAY = Calendar.getInstance(Language.getLocale()).getFirstDayOfWeek();
    private final static String[] DAY_NAMES = DateFormatSymbols.getInstance(Language.getLocale()).getShortWeekdays();

    private final static String PROP_WEEK_DAYS = "weekDays";
    private final static String PROP_RUN_TIME  = "time";

    public Weekly(EntityRef owner, String title) {
        super(owner, title);

        Map<Integer, Boolean> weekDays = new LinkedHashMap<>();
        for (int day=1; day<=7; day++) weekDays.put(day, false);
        model.addUserProp(PROP_WEEK_DAYS, new codex.type.Map<Integer, Boolean>(Int.class, Bool.class, weekDays) {
            @Override
            public IEditorFactory<codex.type.Map<Integer, Boolean>, Map<Integer, Boolean>> editorFactory() {
                return WeekEditor::new;
            }
        }, false, Access.Select);

        model.addUserProp(PROP_RUN_TIME, new DateTime().setMask(DateFormat.Time.newInstance()), true, Access.Select);

        model.addPropertyGroup(Language.get(Schedule.class, "group@kind"), PROP_WEEK_DAYS, PROP_RUN_TIME);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Boolean> getWeekDays() {
        return (Map<Integer, Boolean>) model.getUnsavedValue(PROP_WEEK_DAYS);
    }

    private Date getRunTime() {
        return (Date) model.getUnsavedValue(PROP_RUN_TIME);
    }

    @Override
    protected String calcTitle() {
        Map<Integer, Boolean> weekDays = getWeekDays();
        String days = weekDays.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(entry -> DAY_NAMES[entry.getKey()])
                .collect(Collectors.joining(","));
        Date time = getRunTime();
        if (days.isEmpty()) days = "?";
        return time == null ? null : MessageFormat.format(getFormat(), days, Daily.TIME_FORMAT.format(time));
    }

    @Override
    protected Date calcTime() {
        Date now  = new Date();
        Date last = getLastTime();
        Calendar  calendar = Calendar.getInstance();

        Date date = DateTime.trunc(last == null ? now : last);

        Map<Integer, Boolean> weekDays = getWeekDays();
        Date time = getRunTime();
        if (time == null || !weekDays.values().contains(true)) {
            return null;
        } else {
            calendar.setTime(new Date(date.getTime() + time.getTime()));

            while (!weekDays.get(calendar.get(Calendar.DAY_OF_WEEK)) || calendar.getTime().compareTo(now) <= 0) {

                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
            return calendar.getTime();
        }
    }


    private static class WeekEditor extends AbstractEditor<codex.type.Map<Integer, Boolean>, Map<Integer, Boolean>> {

        private Map<Integer, ToggleButton> buttonSet;

        WeekEditor(PropertyHolder<codex.type.Map<Integer, Boolean>, Map<Integer, Boolean>> propHolder) {
            super(propHolder);
        }

        @Override
        public Box createEditor() {
            buttonSet = createButtons();

            JPanel wrapper = new JPanel();
            wrapper.setLayout(new GridLayout(1, 7, 1, 0));

            Box box = new Box(BoxLayout.LINE_AXIS) {
                { add(wrapper); }

                @Override
                public java.awt.Dimension getPreferredSize() {
                    return wrapper.getPreferredSize();
                }
            };
            buttonSet.forEach((dayIdx, button) -> wrapper.add(button));
            return box;
        }

        Map<Integer, ToggleButton> createButtons() {
            Map<Integer, ToggleButton> buttonMap = new LinkedHashMap<>();

            for (int i = Weekly.FIRST_WEEK_DAY-1; i < FIRST_WEEK_DAY+6; i++) {
                int dayIdx = i % 7 + 1;
                String dayName = DAY_NAMES[dayIdx];
                boolean isWeekend = dayIdx == 1 || dayIdx == 7;

                ToggleButton button = new ToggleButton(
                        ImageUtils.createBadge(dayName, isWeekend ? Color.decode("#DE5347") : Color.decode("#3399FF"), Color.WHITE),
                        null, false
                ) {{
                    setLayout(new BorderLayout(0, 0));
                    button.getParent().add(button, BorderLayout.CENTER);
                }};

                buttonMap.put(dayIdx, button);
                button.addActionListener(e -> {
                    Map<Integer, Boolean> values = propHolder.getPropValue().getValue();
                    if (values.get(dayIdx) != button.isChecked()) {
                        values.put(dayIdx, button.isChecked());
                        propHolder.setValue(values);
                    }
                });
            }
            return buttonMap;
        }

        @Override
        public void setValue(Map<Integer, Boolean> value) {
            value.forEach((dayIdx, enabled) -> buttonSet.get(dayIdx).setChecked(enabled));
        }
    }
}
