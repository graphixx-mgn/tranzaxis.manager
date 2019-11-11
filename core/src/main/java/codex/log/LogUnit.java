package codex.log;

import codex.component.button.DialogButton;
import codex.component.button.IButton;
import codex.component.button.PushButton;
import codex.component.button.ToggleButton;
import codex.component.dialog.Dialog;
import codex.component.layout.WrapLayout;
import codex.component.render.GeneralRenderer;
import codex.context.ContextView;
import codex.context.IContext;
import codex.editor.IEditor;
import codex.presentation.SelectorTable;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.supplier.IDataSupplier;
import codex.type.DateTime;
import codex.type.IComplexType;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogUnit extends AbstractUnit implements WindowStateListener, AdjustmentListener {

    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    private final static Pattern MULTILINE_PATTERN = Pattern.compile("\\n.*", Pattern.DOTALL);

    private final static String QUERY = "SELECT * FROM EVENTLOG WHERE {0} ORDER BY TIME";

    private final static Map<String, String> columnNames = new LinkedHashMap<String, String>(){{
        put("TIME",    Language.get(LogUnit.class,"column@time"));
        put("MESSAGE", Language.get(LogUnit.class,"column@message"));
    }};

    private final static ImageIcon IMAGE_LOG_UNIT  = ImageUtils.resize(ImageUtils.getByPath("/images/log.png"), 17, 17);
    private final static ImageIcon IMAGE_CLIPBOARD = ImageUtils.getByPath("/images/paste.png");
    private final static ImageIcon IMAGE_HIDEALL   = ImageUtils.getByPath("/images/hide.png");
    private final static ImageIcon IMAGE_SHOWALL   = ImageUtils.getByPath("/images/show.png");
    private final static ImageIcon IMAGE_EXPORT    = ImageUtils.getByPath("/images/export.png");
    private final static ImageIcon IMAGE_SET_FROM  = ImageUtils.combine(
            ImageUtils.getByPath("/images/calendar.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 0.4f),
            SwingConstants.SOUTH_EAST
    );
    private final static ImageIcon IMAGE_SET_TO   = ImageUtils.combine(
            ImageUtils.getByPath("/images/calendar.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/up.png"), 0.4f),
            SwingConstants.NORTH_EAST
    );
    private final static ImageIcon IMAGE_HIDE     = ImageUtils.getByPath("/images/unavailable.png");
    private final static ImageIcon IMAGE_STACK    = ImageUtils.resize(ImageUtils.getByPath("/images/stack.png"), 0.7f);

    private final static Color DEBUG_COLOR = Color.GRAY;
    private final static Color WARN_COLOR  = Color.decode("#AA3333");
    private final static Color ERROR_COLOR = Color.decode("#FF3333");

    private final static LogUnit INSTANCE = new LogUnit();
    public  static LogUnit getInstance() {
        return INSTANCE;
    }

    private final JFrame frame = new JFrame();
    private boolean      frameState;
    private Connection   connection;
    {
        try {
            connection = getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private TimeFilter    timeFilter  = new TimeFilter();
    private LevelFilter   levelFilter = new LevelFilter();
    private ContextFilter contextFilter = new ContextFilter();
    private final List<LogUnit.Filter> filters = Arrays.asList(timeFilter, levelFilter, contextFilter);
    private final EventLogSupplier     supplier = new EventLogSupplier(connection) {{
        setQuery(getQuery());
    }};
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new SelectorTable(tableModel);
    private final int    iconSize = (int) (table.getRowHeight() * 0.7);
    private       Level  maxLevel = Level.Debug;

    private LogUnit() {
        Logger.getLogger().debug("Initialize unit: Logger");

        table.setDefaultRenderer(String.class, new GeneralRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String message = MULTILINE_PATTERN.matcher(value.toString()).replaceAll(" [...]");

                JLabel label = (JLabel) super.getTableCellRendererComponent(table, message, isSelected, hasFocus, row, column);
                Level  level = getRowLevel(row);

                if (level == Level.Error) {
                    label.setBackground(Color.decode("#FFDDDD"));
                }
                if (column == 0) {
                    label.setIcon(ImageUtils.resize(level.getIcon(), iconSize, iconSize));
                }
                if (column == 1) {
                    String[]  contexts = tableModel.getValueAt(row,2).toString().split(",", -1);
                    String    lastContextId = contexts[contexts.length-1];
                    ImageIcon ctxIcon = Logger.getContextRegistry().getContext(lastContextId).getIcon();
                    if (tableModel.getValueAt(row,4) == null || tableModel.getValueAt(row,4).equals("")) {
                        label.setIcon(ImageUtils.resize(ctxIcon, iconSize, iconSize));
                    } else {
                        label.setIcon(ImageUtils.resize(
                                ImageUtils.combine(ctxIcon, IMAGE_STACK, SwingConstants.SOUTH_EAST),
                                iconSize, iconSize
                        ));
                    }
                }
                switch (level) {
                    case Debug:
                        label.setForeground(DEBUG_COLOR);
                        break;
                    case Warn:
                        label.setForeground(WARN_COLOR);
                        break;
                    case Error:
                        label.setForeground(ERROR_COLOR);
                        break;
                    default:
                }
                return label;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    final String time    = tableModel.getValueAt(row, 0).toString();
                    final String message = tableModel.getValueAt(row, 3).toString();
                    final String stack   = tableModel.getValueAt(row, 4).toString().trim();
                    final Level  level   = getRowLevel(row);
                    Dialog msgDialog = createMessageDialog(level, time, message, stack);
                    msgDialog.pack();
                    msgDialog.setVisible(true);
                }
            }
        });

        frame.setTitle(Language.get("title"));
        frame.setIconImage(ImageUtils.getByPath("/images/log.png").getImage());
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel toolBar = new JPanel(new BorderLayout());
        toolBar.setBorder(new EmptyBorder(6, 3, 3, 6));

        // Filters
        Box  filterBar = new Box(BoxLayout.LINE_AXIS);
        filterBar.add(timeFilter.createView());
        filterBar.add(levelFilter.createView());
        toolBar.add(filterBar, BorderLayout.CENTER);
        toolBar.add(contextFilter.createView(), BorderLayout.SOUTH);

        // Commands
        PushButton hide = new PushButton(
                ImageUtils.resize(IMAGE_HIDEALL, 26, 26),
                null
        );
        hide.setHint(Language.get(LogUnit.class, "command@hidepast"));
        hide.addActionListener(event -> {
            timeFilter.setFromTime(new Date());
            timeFilter.setToTime(null);
        });

        PushButton show = new PushButton(
                ImageUtils.resize(IMAGE_SHOWALL, 26, 26),
                null
        );
        show.setHint(Language.get(LogUnit.class, "command@showall"));
        show.addActionListener(event -> {
            timeFilter.setFromTime(getFirstRecord());
            timeFilter.setToTime(null);
        });

        PushButton export = new PushButton(
                ImageUtils.resize(IMAGE_EXPORT, 26, 26),
                null
        );
        export.setHint(Language.get(LogUnit.class, "command@export"));
        export.addActionListener(event -> {
            try (
                EventLogSupplier supplier = new EventLogSupplier(getConnection()) {
                    @Override
                    protected String prepareQuery(String query) {
                        return query;
                    }
                }
            ) {
                supplier.setQuery(getQuery());
                HTMLExporter.export(supplier);
            } catch (IOException | SQLException | IDataSupplier.LoadDataException e) {
                e.printStackTrace();
            }
        });

        toolBar.add(new JPanel() {{
            add(hide);
            add(show);
            add(export);
        }}, BorderLayout.EAST);

        // Popup menu
        JPopupMenu popupMenu   = new JPopupMenu();
        JMenuItem  setFromTime = new JMenuItem(Language.get(LogUnit.class, "popup@from"), IMAGE_SET_FROM);
        setFromTime.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int row = table.getSelectedRow();
                try {
                    final Date time = DATE_FORMAT.parse(tableModel.getValueAt(row, 0).toString());
                    timeFilter.setFromTime(time);
                } catch (ParseException e) {
                    //
                }
            }
        });
        JMenuItem setToTime = new JMenuItem(Language.get(LogUnit.class, "popup@to"), IMAGE_SET_TO);
        setToTime.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int row = table.getSelectedRow();
                try {
                    final Date time = DATE_FORMAT.parse(tableModel.getValueAt(row, 0).toString());
                    timeFilter.setToTime(time);
                } catch (ParseException e) {
                    //
                }
            }
        });
        JMenuItem hideContext = new JMenuItem();
        hideContext.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                contextFilter.hideContext((Class<? extends IContext>) ((JMenuItem) event.getSource()).getClientProperty("context"));
            }
        });

        popupMenu.add(setFromTime);
        popupMenu.add(setToTime);
        popupMenu.addSeparator();
        popupMenu.add(hideContext);
        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> {
                    int rowAtPoint = table.rowAtPoint(SwingUtilities.convertPoint(popupMenu, new Point(0, 0), table));
                    if (rowAtPoint > -1) {
                        table.setRowSelectionInterval(rowAtPoint, rowAtPoint);
                        String[]  contexts = tableModel.getValueAt(rowAtPoint, 2).toString().split(",", -1);
                        String    lastContextId = contexts[contexts.length-1];
                        Logger.ContextInfo ctxInfo = Logger.getContextRegistry().getContext(lastContextId);

                        hideContext.setText(MessageFormat.format(
                                Language.get(LogUnit.class, "popup@hide"),
                                new ContextView(ctxInfo.getClazz()).getTitle()
                        ));
                        hideContext.setIcon(ImageUtils.resize(
                                ImageUtils.combine(
                                        ctxInfo.getIcon(),
                                        IMAGE_HIDE
                                ),
                                0.7f
                        ));
                        hideContext.putClientProperty("context", ctxInfo.getClazz());
                        popupMenu.pack();
                    }
                });
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        table.setComponentPopupMenu(popupMenu);

        frame.getContentPane().add(toolBar, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(table) {{
            getViewport().setBackground(Color.WHITE);
            setBorder(new CompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    new MatteBorder(1, 1, 1, 1, Color.GRAY)
            ));
            getVerticalScrollBar().addAdjustmentListener(LogUnit.this);

            final BoundedRangeModel brm = getVerticalScrollBar().getModel();
            ((Logger) Logger.getLogger(Logger.class.getTypeName())).addAppendListener(event -> {
                SwingUtilities.invokeLater(() -> {
                    int extent  = brm.getExtent();
                    int maximum = brm.getMaximum();
                    int current = brm.getValue();
                    if (frame.isVisible() && extent + current == maximum) {
                        try {
                            readNextPage();
                            SwingUtilities.invokeLater(() -> {
                                brm.setValueIsAdjusting(true);
                                brm.setValue(Integer.MAX_VALUE);
                                brm.setValueIsAdjusting(false);
                            });
                        } catch (IDataSupplier.LoadDataException e) {
                            e.printStackTrace();
                        }
                    }
                });
            });
        }}, BorderLayout.CENTER);

        AtomicBoolean frameCreated = new AtomicBoolean(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent event) {
                maxLevel = Level.Debug;
                notifyUnit();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                frameCreated.set(true);
                super.windowOpened(e);
            }
        });
    }

    @Override
    public JComponent createViewport() {
        JLabel label = new JLabel(
                Language.get("title"),
                IMAGE_LOG_UNIT, SwingConstants.CENTER
        ) {{
            setBorder(new EmptyBorder(new Insets(2, 10, 2, 10)));
        }};
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!frame.isVisible()) {
                    frame.setVisible(true);
                } else {
                    frame.setState(Frame.NORMAL);
                    frame.toFront();
                    frame.requestFocus();
                }
            }
        });
        ((Logger) org.apache.log4j.Logger.getLogger(Logger.class)).addAppendListener(event -> {
            if (!frame.isActive() && event.getLevel().toInt() > maxLevel.log4jLevel.toInt()) {
                maxLevel = Level.fromSysLevel(event.getLevel());
                notifyUnit();
            }
        });
        return label;
    }

    private void notifyUnit() {
        JComponent view = getViewport();
        if (view != null) {
            JLabel label = (JLabel) view;
            if (maxLevel != Level.Debug) {
                label.setIcon(ImageUtils.resize(maxLevel.getIcon(), 17, 17));
            } else {
                label.setIcon(IMAGE_LOG_UNIT);
            }
        }
    }

    @Override
    public void viewportBound() {
        final GraphicsEnvironment graphEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice[] graphDevs = graphEnv.getScreenDevices();
        if (graphDevs.length > 1) {
            Logger.getLogger().debug("Detected multi screens configuration. EventLog window moved to 2ND device");
            final Insets scnMax = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
            int taskBarSize = scnMax.bottom;
            final Rectangle bounds = graphDevs[1].getDefaultConfiguration().getBounds();
            frame.setSize(bounds.width, bounds.height-taskBarSize);
            frame.setLocation(bounds.x, bounds.y);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            Logger.getLogger().debug("Detected single screen configuration. Event Log opened at center of 1ST device");
            frame.setSize(new Dimension(1000, 700));
            frame.setMinimumSize(new Dimension(500, 400));  
            frame.setLocationRelativeTo(null);
        }
        SwingUtilities.getWindowAncestor(view).addWindowStateListener(this);
    }
    
    @Override
    public void windowStateChanged(WindowEvent event) {
        if (event.getNewState() == JFrame.ICONIFIED) {
            frameState = frame.isVisible();
            frame.setVisible(false);
        } else {
            frame.setVisible(frameState);
        }
    }

    private Connection getConnection() throws SQLException {
        Path dbFilePath = Paths.get(
                System.getProperty("user.home"),
                ((LogManagementService) Logger.getLogger()).getOption("file")
        );
        return DriverManager.getConnection("jdbc:sqlite:"+ dbFilePath);
    }

    private void applyFilters() {
        tableModel.setRowCount(0);
        supplier.setQuery(getQuery());
        SwingUtilities.invokeLater(() -> {
            table.setCursor(new Cursor(Cursor.WAIT_CURSOR));
            try {
                readNextPage();
            } catch (IDataSupplier.LoadDataException e) {
                e.printStackTrace();
            }
            table.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        });
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent event) {
        if (!event.getValueIsAdjusting()) {
            JScrollBar scrollBar = (JScrollBar) event.getAdjustable();
            int extent = scrollBar.getModel().getExtent();
            int maximum = scrollBar.getModel().getMaximum();
            int current = scrollBar.getModel().getValue();

            if (extent + current == maximum && supplier.available(IDataSupplier.ReadDirection.Forward)) {
                SwingUtilities.invokeLater(() -> {
                    table.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    try {
                        readNextPage();
                    } catch (IDataSupplier.LoadDataException e) {
                        e.printStackTrace();
                    }
                    table.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                });
            }
        }
    }

    private void readNextPage() throws IDataSupplier.LoadDataException {
        final List<Map<String, String>> data = supplier.getNext();
        if (!data.isEmpty()) {
            if (tableModel.getColumnCount() == 0) {
                initColumnModel(data.get(0).keySet());
            }
            data.forEach(rowMap -> tableModel.addRow(rowMap.values().toArray()));
        }
    }

    private String getQuery() {
        return MessageFormat.format(
                QUERY,
                filters.stream()
                    .map(IFilter::getConstraint)
                    .collect(Collectors.joining(" AND "))
        );
    }

    private void initColumnModel(Set<String> columns) {
        columns.forEach(column -> tableModel.addColumn(columnNames.getOrDefault(column, column)));
        Collections.list(table.getColumnModel().getColumns()).forEach(tableColumn -> {
            String columnName = tableColumn.getIdentifier().toString();
            if (!columnNames.containsValue(columnName)) {
                table.getColumnModel().removeColumn(tableColumn);
            }
        });
        TableColumn col = table.getColumnModel().getColumn(0);
        col.setPreferredWidth(200);
        col.setMaxWidth(200);
    }

    private Level getRowLevel(int row) {
        return Level.fromSysLevel(org.apache.log4j.Level.toLevel((tableModel.getValueAt(row,1).toString())));
    }

    private Date getFirstRecord() {
        try (ResultSet resultSet = getConnection().prepareStatement("SELECT MIN(TIME) FROM EVENTLOG").executeQuery()) {
            if (resultSet.next()) {
                return DATE_FORMAT.parse(resultSet.getString(1));
            }
        } catch (SQLException | ParseException e) {
            e.printStackTrace();
        }
        return new Date(0);
    }

    private Dialog createMessageDialog(Level level, String time, String text, String stack) {
        DialogButton[] buttons = stack.isEmpty() ?
                new DialogButton[] {
                        Dialog.Default.BTN_CLOSE.newInstance()
                } :
                new DialogButton[] {
                        Dialog.Default.BTN_OK.newInstance(IMAGE_CLIPBOARD, Language.get(LogUnit.class, "dialog@copystack")),
                        Dialog.Default.BTN_CLOSE.newInstance()
                };

        return new Dialog(
                frame,
                level.getIcon(),
                MessageFormat.format(Language.get(LogUnit.class, "dialog@title"), time),
                new JPanel(new BorderLayout()) {{
                    JTextPane messagePane = new JTextPane() {
                        {
                            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                            setEditable(false);
                            setText(text);
                            setCaretPosition(0);
                        }
                        @Override
                        public Dimension getPreferredScrollableViewportSize() {
                            int dataHeight = super.getPreferredScrollableViewportSize().height;
                            return new Dimension(
                                    stack.isEmpty() ? 700 : 900,
                                    dataHeight < 100 ? 100 : 400
                            );
                        }
                    };
                    add(new JScrollPane() {
                        {
                            setViewportView(messagePane);
                            setBorder(new CompoundBorder(
                                    new EmptyBorder(3, 5, 3, 5),
                                    new LineBorder(Color.GRAY, 1)
                            ));
                            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                        }
                    }, BorderLayout.NORTH);

                    if (!stack.isEmpty()) {
                        JTextPane stackPane = new JTextPane() {
                            {
                                setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                                setText(stack);
                                setOpaque(false);
                                setEditable(false);
                                setCaretPosition(0);
                            }
                            @Override
                            public Dimension getPreferredScrollableViewportSize() {
                                return new Dimension(700, Math.min(super.getPreferredSize().height, 200));
                            }
                        };
                        add(new JScrollPane() {
                            {
                                setViewportView(stackPane);
                                setBorder(new CompoundBorder(
                                        new EmptyBorder(3, 3, 3, 3),
                                        new TitledBorder(new LineBorder(Color.GRAY, 1), Language.get(LogUnit.class, "dialog@stack"))
                                ));
                                setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                                setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                            }
                        }, BorderLayout.CENTER);
                    }
                }},
                event -> {
                    if (event.getID() == Dialog.OK) {
                        Toolkit.getDefaultToolkit()
                                .getSystemClipboard()
                                .setContents(
                                        new StringSelection(stack),
                                        null
                                );
                        }
                },
                buttons
        ) {{
            setResizable(false);
        }};
    }


    interface IFilter {
        String getConstraint();
    }


    abstract class Filter implements IFilter {
        abstract Container createView();
    }


    private class TimeFilter extends Filter {

        private static final String PROP_FROM_TIME = "from";
        private static final String PROP_TO_TIME   = "to";

        private PropertyHolder fromTime = new PropertyHolder<>(PROP_FROM_TIME, new DateTime(null), false);
        private PropertyHolder toTime   = new PropertyHolder<>(PROP_TO_TIME,   new DateTime(null), false);
        private IPropertyChangeListener listener = (name, oldValue, newValue) -> applyFilters();

        {
            fromTime.addChangeListener(listener);
            toTime.addChangeListener(listener);
        }

        @Override
        @SuppressWarnings("unchecked")
        Container createView() {
            return new JPanel() {{
                setLayout(new GridLayout(2, 2));
                setBorder(new TitledBorder(new LineBorder(Color.GRAY, 1), Language.get(LogUnit.class, "filter@time")));

                // From time editor
                IEditor fromEditor = fromTime.getPropValue().editorFactory().newInstance(fromTime);
                JLabel  fromLabel  = fromEditor.getLabel();
                Box     fromBox    = fromEditor.getEditor();

                fromLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                fromBox.setAlignmentX(Component.LEFT_ALIGNMENT);

                // To time editor
                IEditor toEditor = toTime.getPropValue().editorFactory().newInstance(toTime);
                JLabel  toLabel  = toEditor.getLabel();
                Box     toBox    = toEditor.getEditor();

                toLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                toBox.setAlignmentX(Component.LEFT_ALIGNMENT);

                add(fromLabel);
                add(toLabel);
                add(fromBox);
                add(toBox);
            }};
        }

        @Override
        public String getConstraint() {
            Date startTime = IComplexType.coalesce(
                    ((DateTime) fromTime.getPropValue()).getValue(),
                    new Date(Logger.getSessionStartTimestamp())
            );
            Date endTime = IComplexType.coalesce(
                    ((DateTime) toTime.getPropValue()).getValue(),
                    new Date(Long.MAX_VALUE)
            );
            return MessageFormat.format(
                    "TIME >= ''{0}'' AND TIME <= ''{1}''",
                    DATE_FORMAT.format(startTime),
                    DATE_FORMAT.format(endTime)
            );
        }

        @SuppressWarnings("unchecked")
        void setFromTime(Date date) {
            fromTime.setValue(date);
        }

        @SuppressWarnings("unchecked")
        void setToTime(Date date) {
            toTime.setValue(date);
        }
    }


    private class LevelFilter extends Filter {

        private Map<ToggleButton, Level> buttons = Arrays.stream(new Level[] {Level.Debug, Level.Info, Level.Warn, Level.Error})
                .collect(Collectors.toMap(
                        level -> new ToggleButton(
                                ImageUtils.resize(level.getIcon(), 0.7f),
                                "",
                                true
                        ) {{
                            addActionListener(e -> applyFilters());
                        }},
                        level -> level,
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new
                ));

        @Override
        Container createView() {
            return new JPanel() {{
                setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                setBorder(new TitledBorder(new LineBorder(Color.GRAY, 1), Language.get(LogUnit.class, "filter@level")));

                add(Box.createHorizontalStrut(5));
                buttons.forEach((toggleButton, level) -> {
                    add(toggleButton);
                    add(Box.createHorizontalStrut(5));
                });
            }};
        }

        @Override
        public String getConstraint() {
            return MessageFormat.format(
                    "LEVEL IN ({0})",
                    buttons.entrySet().stream()
                            .filter(entry -> entry.getKey().isChecked())
                            .map(entry -> MessageFormat.format("''{0}''", entry.getValue().getSysLevel().toString()))
                            .collect(Collectors.joining(", "))
            );
        }
    }


    private class ContextFilter extends Filter {

        private Collection<Class<? extends IContext>> systemContexts = Logger.getContextRegistry().getContexts();
        private List<Class<? extends IContext>> hiddenContexts = new LinkedList<>();
        private Container view = new JPanel() {{
            setLayout(new WrapLayout(FlowLayout.LEFT, 3, 3));
            setBorder(new TitledBorder(new LineBorder(Color.GRAY, 1), Language.get(LogUnit.class, "filter@context")));
            setVisible(false);
        }};

        @Override
        Container createView() {
            return view;
        }

        @Override
        public String getConstraint() {
            return MessageFormat.format(
                    "({0})",
                    hiddenContexts.containsAll(systemContexts) ?
                            "1=0" :
                            systemContexts.stream()
                                    .filter(ctxClass -> !hiddenContexts.contains(ctxClass))
                                    .map(ctxClass -> MessageFormat.format(
                                            "INSTR(CONTEXT, ''{0}'') = LENGTH(CONTEXT) - LENGTH(''{0}'') + 1",
                                            Logger.getContextRegistry().getContext(ctxClass).getId()
                                    ))
                            .collect(Collectors.joining(" OR "))
            );
        }

        void hideContext(Class<? extends IContext> contextClass) {
            hiddenContexts.add(contextClass);
            view.setVisible(!hiddenContexts.isEmpty());
            view.add(createContextLabel(contextClass));
            view.revalidate();
            view.repaint();
            applyFilters();
        }

        void showContext(Class<? extends IContext> contextClass) {
            hiddenContexts.remove(contextClass);
            view.setVisible(!hiddenContexts.isEmpty());
            view.revalidate();
            view.repaint();
            applyFilters();
        }

        private JComponent createContextLabel(Class<? extends IContext> contextClass) {
            return new Box(BoxLayout.LINE_AXIS) {{
                add(new CloseButton() {{
                    setAlignmentY(Component.CENTER_ALIGNMENT);
                    setBorder(new EmptyBorder(5, 5, 5, 0));
                    addActionListener(new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            view.remove(getParent());
                            showContext(contextClass);
                        }
                    });
                }}, BorderLayout.NORTH);

                add(new JLabel(
                        new ContextView(contextClass).getTitle(),
                        ImageUtils.resize(Logger.getContextRegistry().getContext(contextClass).getIcon(), 0.5f),
                        SwingConstants.LEFT
                ) {{
                    setAlignmentY(Component.CENTER_ALIGNMENT);
                    setBorder(new EmptyBorder(5,5,5,5));
                }});

                setOpaque(false);
                setBackground(IButton.PRESS_COLOR);
                setBorder(
                        IButton.PRESS_BORDER
                );
            }
                @Override
                public void paintComponent(Graphics g) {
                    g.setColor(getBackground());
                    Insets insets = getInsets();
                    g.fillRect(
                            insets.left,
                            insets.top,
                            getWidth()-insets.right*2,
                            getHeight()-insets.bottom*2
                    );
                }
            };
        }

        private class CloseButton extends JButton implements IButton {

            private final ImageIcon activeIcon;
            private final ImageIcon passiveIcon;

            CloseButton() {
                super();
                this.activeIcon   = ImageUtils.getByPath("/images/endtask.png");
                this.passiveIcon  = ImageUtils.grayscale(this.activeIcon);
                setIcon(passiveIcon);
                setFocusable(false);
                setFocusPainted(false);
                setContentAreaFilled(false);
                setRolloverEnabled(true);
                setBorder(new EmptyBorder(0, 0, 0, 0));

                getModel().addChangeListener((ChangeEvent event) -> {
                    ButtonModel model = (ButtonModel) event.getSource();
                    if (model.isRollover()) {
                        setIcon(activeIcon);
                    } else {
                        setIcon(passiveIcon);
                    }
                });
            }

            @Override
            public void setHint(String text) {
                setToolTipText(text);
            }

            @Override
            public void click() {
                doClick();
            }
        }
    }
}
