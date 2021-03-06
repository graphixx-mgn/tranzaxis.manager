package codex.editor;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.button.IButton;
import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.property.PropertyHolder;
import codex.type.DateTime;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import com.github.lgooddatepicker.components.CalendarPanel;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.privatejgoodies.forms.factories.CC;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DateFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

@ThreadSafe
public class DateTimeEditor extends AbstractEditor<DateTime, Date> {

    private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

    private static final ImageIcon PICK = ImageUtils.getByPath("/images/calendar.png");
    private static final ImageIcon CURR = ImageUtils.getByPath("/images/now.png");
    private static final ImageIcon NEXT_MONTH = ImageUtils.resize(ImageUtils.getByPath("/images/next.png"), 0.7f);
    private static final ImageIcon PREV_MONTH = ImageUtils.resize(ImageUtils.getByPath("/images/prev.png"), 0.7f);
    private static final ImageIcon NEXT_YEAR  = ImageUtils.resize(ImageUtils.getByPath("/images/end.png"), 0.7f);
    private static final ImageIcon PREV_YEAR  = ImageUtils.resize(ImageUtils.getByPath("/images/begin.png"), 0.7f);

    private JTextField   textField;
    private final JLabel signDelete;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public DateTimeEditor(PropertyHolder<DateTime, Date> propHolder) {
        super(propHolder);
        TimePicker  timePicker = new TimePicker();
        CurrentTime currentTime = new CurrentTime();

        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"),
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));
        signDelete.setCursor(Cursor.getDefaultCursor());

        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(signDelete, BorderLayout.WEST);

        signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        textField.add(controls, BorderLayout.EAST);

        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                propHolder.setValue(null);
            }
        });

        addCommand(currentTime);
        addCommand(timePicker);
        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    timePicker.execute(propHolder);
                }
            }
        });
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);
        textField.addFocusListener(this);

        PlaceHolder placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField, PlaceHolder.Show.ALWAYS);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(textField);
        return container;
    }

    private EditorCommandButton addSysCommand(EditorCommand<DateTime, Date> command) {
        final EditorCommandButton button = new EditorCommandButton(command);
        commands.add(command);
        command.setContext(propHolder);
        return button;
    }

    @Override
    protected void updateEditable(boolean editable) {
        textField.setForeground(editable && !propHolder.isInherited() ? COLOR_NORMAL : COLOR_DISABLED);
        textField.setBackground(editable && !propHolder.isInherited() ? Color.WHITE  : null);
        getEditor().setBackground(editable && !propHolder.isInherited() ? Color.WHITE  : null);
    }

    @Override
    protected void updateValue(Date date) {
        textField.setText(date == null ? "" : propHolder.getOwnPropValue().getMask().getFormat().format(date));
        if (signDelete!= null) {
            signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        }
    }

    @Override
    public void focusGained(FocusEvent event) {
        if (isEditable()) {
            super.focusGained(event);
            if (signDelete != null) {
                signDelete.setVisible(!propHolder.isEmpty());
            }
        }
    }

    @Override
    public void focusLost(FocusEvent event) {
        if (isEditable()) {
            super.focusLost(event);
        }
        signDelete.setVisible(false);
    }

    private boolean showDate() {
        switch (propHolder.getOwnPropValue().getMask().getFormat()) {
            case Full: return true;
            default:   return false;
        }
    }

    private class CurrentTime extends EditorCommand<DateTime, Date> {

        private CurrentTime() {
            super(CURR, null);
            activator = holder -> new CommandStatus(true, null, !isEditable());
        }

        @Override
        public void execute(PropertyHolder<DateTime, Date> context) {
            Date newValue = new Date();
            if (!showDate()) {
                newValue = new Date(newValue.getTime() % MILLIS_PER_DAY);
            }
            context.setValue(newValue);
        }
    }

    private class TimePicker extends EditorCommand<DateTime, Date> {

        private final DatePickerSettings settings  = new DatePickerSettings(Language.getLocale()) {{
            setVisibleClearButton(false);
            setVisibleTodayButton(false);
            setEnableMonthMenu(false);
            setEnableYearMenu(false);

            setFontCalendarWeekdayLabels(IEditor.FONT_BOLD);
            setColorBackgroundWeekdayLabels(Color.LIGHT_GRAY, true);
            setColor(DatePickerSettings.DateArea.CalendarBackgroundSelectedDate, Color.decode("#C0DCF3"));
            setColor(DatePickerSettings.DateArea.CalendarBorderSelectedDate, Color.decode("#90C8F6"));
        }};
        private final SpinnerDateModel timeSpinner = new SpinnerDateModel();
        private final CalendarPanel calendarPanel  = new CalendarPanel(settings) {{
            Container headerControls = getPreviousMonthButton().getParent();
            //Prev year
            headerControls.remove(0);
            headerControls.add(new PushButton(PREV_YEAR, null) {{
                button.setBorder(new EmptyBorder(2, 4, 2, 4));
                addActionListener(getPreviousYearButton().getActionListeners()[0]);
            }}, CC.xy(1, 1), 0);
            //Next year
            headerControls.remove(4);
            headerControls.add(new PushButton(NEXT_YEAR, null) {{
                button.setBorder(new EmptyBorder(2, 4, 2, 4));
                addActionListener(getNextYearButton().getActionListeners()[0]);
            }}, CC.xy(7, 1), 4);
            //Prev month
            headerControls.remove(1);
            headerControls.add(new PushButton(PREV_MONTH, null) {{
                button.setBorder(new EmptyBorder(2, 4, 2, 4));
                addActionListener(getPreviousMonthButton().getActionListeners()[0]);
            }}, CC.xy(2, 1), 1);
            //Next month
            headerControls.remove(3);
            headerControls.add(new PushButton(NEXT_MONTH, null) {{
                button.setBorder(new EmptyBorder(2, 4, 2, 4));
                addActionListener(getNextMonthButton().getActionListeners()[0]);
            }}, CC.xy(6, 1), 3);
        }};
        private final DialogButton apply = Dialog.Default.BTN_OK.newInstance("");
        private final DialogButton now   = Dialog.Default.BTN_OK.newInstance(CURR, "");
        private final Component invoker  = DateTimeEditor.this.textField.getParent();
        private final JPopupMenu popup   = new JPopupMenu() {{
            setBorder(IButton.PRESS_BORDER);
            add(new JPanel(new BorderLayout()) {{
                JSpinner spinner = new JSpinner(timeSpinner);
                spinner.setFont(IEditor.FONT_VALUE.deriveFont(IEditor.FONT_VALUE.getSize()));

                JSpinner.DateEditor editor = new JSpinner.DateEditor(
                        spinner,
                        propHolder.getOwnPropValue().getMask().getFormat().getTimeFormat().toPattern()
                );
                spinner.setEditor(editor);

                DateFormatter formatter = (DateFormatter)editor.getTextField().getFormatter();
                formatter.setAllowsInvalid(false);
                formatter.setOverwriteMode(true);

                add(calendarPanel, BorderLayout.NORTH);
                add(new JPanel(new BorderLayout()) {{
                    setBorder(new EmptyBorder(0, 5, 0, 5));
                    int defWidth = calendarPanel.getComponent(0).getPreferredSize().width;
                    if (!showDate()) {
                        add(Box.createRigidArea(new Dimension(defWidth, 10)), BorderLayout.NORTH);
                    }
                    add(spinner, BorderLayout.CENTER);
                }}, BorderLayout.CENTER);
                add(new JPanel(){{
                    add(now);
                    add(apply);
                }}, BorderLayout.SOUTH);
            }});
        }};

        private TimePicker() {
            super(PICK, null);
            activator = holder -> new CommandStatus(true, null, !isEditable());
            now.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Date currDate = new Date();
                    SwingUtilities.invokeLater(() -> {
                        calendarPanel.setSelectedDate(convertDate(currDate));
                        timeSpinner.setValue(currDate);
                    });
                }
            });
            apply.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Date time = new Date(timeSpinner.getDate().getTime() % MILLIS_PER_DAY);
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(time);

                    if (showDate()) {
                        LocalDate localDate = calendarPanel.getSelectedDate();
                        if (localDate != null) {
                            calendar.set(Calendar.YEAR,  localDate.getYear());
                            calendar.set(Calendar.MONTH, localDate.getMonthValue()-1);
                            calendar.set(Calendar.DAY_OF_MONTH, localDate.getDayOfMonth());
                        }
                    }
                    propHolder.setValue(calendar.getTime());
                    popup.setVisible(false);
                }
            });
        }

        @Override
        public void execute(PropertyHolder<DateTime, Date> context) {
            Date currDate = new Date();
            SwingUtilities.invokeLater(() -> {
                timeSpinner.setValue(IComplexType.coalesce(
                        context.getPropValue().getValue(),
                        currDate
                ));
                calendarPanel.setVisible(showDate());
                calendarPanel.setSelectedDate(convertDate(IComplexType.coalesce(
                        context.getPropValue().getValue(),
                        currDate
                )));
                popup.show(
                        invoker,
                        invoker.getWidth() - popup.getPreferredSize().width - 1,
                        invoker.getHeight()
                );
            });
        }

        private LocalDate convertDate(Date date) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()).toLocalDate();
        }
    }
}
