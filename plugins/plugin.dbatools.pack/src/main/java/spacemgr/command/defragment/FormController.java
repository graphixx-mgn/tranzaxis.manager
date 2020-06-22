package spacemgr.command.defragment;

import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.task.AbstractTaskView;
import codex.task.ITask;
import codex.task.TaskOutput;
import codex.task.TaskView;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.command.objects.TableSpace;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.text.MessageFormat;

public class FormController implements IFormController {

    private final static ImageIcon ICON_LEGEND  = ImageUtils.resize(ImageUtils.getByPath("/images/tbs_map.png"), .6f);
    private final static ImageIcon ICON_GENERAL = ImageUtils.resize(ImageUtils.getByPath("/images/general.png"), .6f);
    private final static ImageIcon ICON_OUTPUT  = ImageUtils.resize(ImageUtils.getByPath("/images/log.png"), .6f);

    private final static String LEGEND      = Language.get("legend@title");
    private final static String LEGEND_SYS  = Language.get("legend@system");
    private final static String LEGEND_USED = Language.get("legend@used");
    private final static String LEGEND_FREE = Language.get("legend@free");
    private final static String LEGEND_SEL  = Language.get("legend@select");
    private final static String LEGEND_HIGH = Language.get("legend@segment");
    private final static String LEGEND_BAD  = Language.get("legend@invalid");

    private final static String TAB_GENERAL = Language.get("tab@general");
    private final static String TAB_OUTPUT  = Language.get("tab@output");

    private final TableSpace tableSpace;
    private final Provider   provider;
    private final MapPanel   mapView;
    private final CellView   cellView;

    private final JLabel     infoLabel = new JLabel() {{
        setBorder(new EmptyBorder(2, 10, 0, 5));
    }};
    private final PushButton showLegend = new PushButton(ICON_LEGEND, LEGEND) {{
        addActionListener(e -> showLegend());
    }};
    private final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP) {{
        setBorder(new EmptyBorder(0, 5, 0, 5));
    }};

    FormController(TableSpace tableSpace) throws SQLException {
        this.tableSpace = tableSpace;
        this.provider   = new Provider(this);
        this.mapView    = new MapPanel(this);
        this.cellView   = new CellView(this);
    }

    @Override
    public TableSpace getTableSpace() {
        return tableSpace;
    }

    @Override
    public IDataProvider getDataProvider() {
        return provider;
    }

    @Override
    public IMapView getMapView() {
        return mapView;
    }

    @Override
    public ICellView getCellView() {
        return cellView;
    }

    @Override
    public JPanel getFormView() {
        infoLabel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                final long blocksInCell = getMapView().getBlocksPerCell();
                final long sizeOfCell   = blocksInCell * getTableSpace().getBlockSize();
                infoLabel.setText(MessageFormat.format(
                        Language.get("info@pattern"),
                        getTableSpace().getPID(),
                        String.valueOf(getTableSpace().getBlocks()),
                        String.valueOf(blocksInCell),
                        FileUtils.formatFileSize(sizeOfCell)
                ));
            }
        });

        tabbedPane.addTab(TAB_GENERAL, ICON_GENERAL, getCellView().getComponent());
        tabbedPane.addTab(TAB_OUTPUT,  ICON_OUTPUT, new JPanel());
        tabbedPane.setEnabledAt(1, false);
        tabbedPane.setDisabledIconAt(1, ImageUtils.grayscale(ICON_OUTPUT));

        return new JPanel(new BorderLayout()) {{
            add(new JPanel() {{
                setBorder(new EmptyBorder(5, 5, 0, 5));
                setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                add(showLegend);
                add(infoLabel);
            }}, BorderLayout.NORTH);
            add(getMapView().getComponent(),  BorderLayout.WEST);
            add(tabbedPane, BorderLayout.CENTER);
        }};
    }

    @Override
    public void initLogOutput(ITask task) {
        tabbedPane.setComponentAt(1, new JPanel(new BorderLayout(5, 5)) {{
            setBorder(new EmptyBorder(5, 5, 5, 5));
            AbstractTaskView taskView = new TaskView(task, null);
            taskView.setBorder(new CompoundBorder(
                    new LineBorder(Color.LIGHT_GRAY, 1),
                    new EmptyBorder(5, 5, 5, 5)
            ));
            add(taskView, BorderLayout.NORTH);
            add(TaskOutput.createOutput(task), BorderLayout.CENTER);
        }});
        tabbedPane.setEnabledAt(1, true);
        tabbedPane.setSelectedIndex(1);
    }

    private void showLegend() {
        new Dialog(
                Dialog.findNearestWindow(),
                ICON_LEGEND,
                LEGEND,
                new JPanel() {{
                    setLayout(new GridLayout(0, 1, 5,5));
                    setBorder(new EmptyBorder(5,5,5,5));
                    add(new JLabel(LEGEND_SYS,  createRect(MapPanel.CELL_SYS), SwingConstants.LEFT));
                    add(new JLabel(LEGEND_USED, createRect(MapPanel.CELL_USED), SwingConstants.LEFT));
                    add(new JLabel(LEGEND_FREE, createRect(MapPanel.CELL_FREE), SwingConstants.LEFT));
                    add(new JLabel(LEGEND_SEL,  createRect(MapPanel.CELL_SEL), SwingConstants.LEFT));
                    add(new JLabel(LEGEND_HIGH, createRect(MapPanel.CELL_HIGH), SwingConstants.LEFT));
                    add(new JLabel(LEGEND_BAD,  createRect(MapPanel.CELL_BAD), SwingConstants.LEFT));
                }},
                null,
                Dialog.Default.BTN_CLOSE
        ).setVisible(true);
    }

    private static ImageIcon createRect(Color fillColor) {
        int size = 15;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(fillColor);
        g.fillRect(0, 0, size-1, size-1);

        g.setColor(Color.GRAY);
        g.drawRect(0, 0, size-1, size-1);
        return new ImageIcon(image);
    }
}
