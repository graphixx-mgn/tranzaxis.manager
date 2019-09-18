package codex.editor;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
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
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DateFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

public class DateTimeEditor extends AbstractEditor<DateTime, Date> {

    private static final ImageIcon PICK = ImageUtils.getByPath("/images/calendar.png");
    private static final ImageIcon CURR = ImageUtils.getByPath("/images/now.png");
    private static final ImageIcon NEXT_MONTH = ImageUtils.resize(ImageUtils.getByPath("/images/next.png"), 0.7f);
    private static final ImageIcon PREV_MONTH = ImageUtils.resize(ImageUtils.getByPath("/images/prev.png"), 0.7f);
    private static final ImageIcon NEXT_YEAR  = ImageUtils.resize(ImageUtils.getByPath("/images/end.png"), 0.7f);
    private static final ImageIcon PREV_YEAR  = ImageUtils.resize(ImageUtils.getByPath("/images/begin.png"), 0.7f);

    private static final String DATETIME_FORMAT = MessageFormat.format(
            "{0} {1}",
            ((SimpleDateFormat) DateFormat.getDateInstance(DateFormat.LONG, Language.getLocale())).toPattern(),
            "HH:mm:ss,SSS"
    );
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat(DATETIME_FORMAT, Language.getLocale());

    private JTextField textField;
    private final JLabel signDelete;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public DateTimeEditor(PropertyHolder<DateTime, Date> propHolder) {
        super(propHolder);
        TimePicker timePicker = new TimePicker();

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

        addCommand(new CurrentTime());
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
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }

    @Override
    public void setValue(Date date) {
        textField.setText(date == null ? "" : FORMATTER.format(date));
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

    private class CurrentTime extends EditorCommand<DateTime, Date> {

        private CurrentTime() {
            super(ImageUtils.resize(CURR, 18, 18), null);
        }

        @Override
        public void execute(PropertyHolder<DateTime, Date> context) {
            context.setValue(new Date());
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
        private final JPopupMenu popup   = new JPopupMenu(){{
            add(new JPanel(new BorderLayout()) {{
                JSpinner spinner = new JSpinner(timeSpinner);
                spinner.setFont(IEditor.FONT_VALUE.deriveFont(IEditor.FONT_VALUE.getSize()*0.9f));

                JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "HH:mm:ss,SSS");
                spinner.setEditor(editor);

                DateFormatter formatter = (DateFormatter)editor.getTextField().getFormatter();
                formatter.setAllowsInvalid(false);
                formatter.setOverwriteMode(true);

                add(new JPanel(new BorderLayout()){{
                    setBorder(new EmptyBorder(0,5,0,5));
                    add(spinner, BorderLayout.CENTER);
                }}, BorderLayout.CENTER);

                add(calendarPanel, BorderLayout.NORTH);
                add(new JPanel(){{
                    add(now);
                    add(apply);
                }}, BorderLayout.SOUTH);
            }});
        }};
        private TimePicker() {
            super(ImageUtils.resize(PICK, 18, 18), null);
            now.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Date currDate = new Date();
                    calendarPanel.setSelectedDate(convertDate(new Date()));
                    timeSpinner.setValue(currDate);
                }
            });
            apply.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LocalDate localDate = calendarPanel.getSelectedDate();
                    if (localDate != null) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(timeSpinner.getDate());
                        calendar.set(Calendar.YEAR,  localDate.getYear());
                        calendar.set(Calendar.MONTH, localDate.getMonthValue()-1);
                        calendar.set(Calendar.DAY_OF_MONTH, localDate.getDayOfMonth());
                        propHolder.setValue(calendar.getTime());
                    }
                    popup.setVisible(false);
                }
            });
        }

        @Override
        public void execute(PropertyHolder<DateTime, Date> context) {
            timeSpinner.setValue(IComplexType.coalesce(
                    context.getPropValue().getValue(),
                    new Date()
            ));
            if (context.getPropValue().getValue() != null) {
                calendarPanel.setSelectedDate(convertDate(context.getPropValue().getValue()));
            }
            popup.show(
                    invoker,
                    invoker.getWidth() - popup.getPreferredSize().width - 1,
                    invoker.getHeight()
            );
        }

        private LocalDate convertDate(Date date) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()).toLocalDate();
        }
    }
}
