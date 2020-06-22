package spacemgr.command.defragment;

import spacemgr.command.objects.Extent;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

class MapPanel extends JPanel implements IFormController.IMapView {

    // Dimensions
    private final static int CELL_SIZE   = 8;
    private final static int MAP_WIDTH   = 50;
    private final static int MAP_HEIGHT  = 60;
    private final static int CELL_COUNT  = MAP_WIDTH * MAP_HEIGHT;

    // Colors
    final static Color CELL_SYS  = Color.MAGENTA;
    final static Color CELL_BAD  = Color.RED;
    final static Color CELL_USED = Color.decode("#90B8FF");
    final static Color CELL_FREE = Color.WHITE;
    final static Color CELL_NONE = Color.LIGHT_GRAY;
    final static Color CELL_SEL  = Color.decode("#2288FF");
    final static Color CELL_HIGH = Color.CYAN;

    private final IFormController controller;
    private final JScrollPane scrollPane;

    private int   selectedCell;
    private int[] highlightedCells = new int[]{};
    private final Map<Integer, List<Extent>> cellMap = new HashMap<>();

    MapPanel(IFormController controller) {
        super();
        this.controller = controller;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                int cellIdx = getCellIndex(e.getY() / CELL_SIZE + 1, e.getX()  / CELL_SIZE + 1);
                if (isCellUsed(cellIdx)) {
                    selectCell(cellIdx);
                } else {
                    selectCell(-1);
                }
            }
        });

        scrollPane = new JScrollPane(MapPanel.this) {
            @Override
            public Dimension getPreferredSize() {
                Dimension defaultDim = super.getPreferredSize();
                if (getSize().height < defaultDim.height) {
                    int scrollBarWidth = scrollPane.getVerticalScrollBar().getPreferredSize().width;
                    return new Dimension(
                            defaultDim.width + scrollBarWidth + 1,
                            defaultDim.height
                    );
                } else {
                    return defaultDim;
                }
            }
        };
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 0),
                new LineBorder(Color.GRAY, 1)
        ));
    }

    @Override
    public Dimension getPreferredSize() {
        int minHeight = CELL_SIZE * (int) Math.ceil(
                ((double) controller.getTableSpace().getBlocks() / getBlocksPerCell() / MAP_WIDTH)
        ) - 1;
        int minWidth  = MAP_WIDTH * CELL_SIZE - 1;
        return new Dimension(minWidth, minHeight);
    }

    @Override
    public JComponent getComponent() {
        return scrollPane;
    }

    @Override
    public int getBlocksPerCell() {
        return (int) Math.ceil(((float) controller.getTableSpace().getBlocks()) / CELL_COUNT);
    }

    @Override
    public void insertExtent(Extent extent) {
        int[] cellIds = getCellsByExtent(extent);
        for (int cellIdx : cellIds) {
            cellMap.computeIfAbsent(cellIdx, index -> new LinkedList<>()).add(extent);
            paintCell(cellIdx, null);
        }
    }

    @Override
    public void removeExtent(Extent extent) {
        int[] cellIds = getCellsByExtent(extent);
        for (int cellIdx : cellIds) {

            cellMap.computeIfAbsent(cellIdx, index -> new LinkedList<>()).remove(extent);
            if (selectedCell == cellIdx) {
                List<Extent> cellExtents = getExtentsByCell(cellIdx);
                if (cellExtents.isEmpty()) {
                    selectCell(-1);
                } else {
                    selectCell(cellIdx);
                }
            }
            paintCell(cellIdx, null);
        }
    }

    @Override
    public void showExtents(List<Extent> extents) {
        highlightCells(extents.parallelStream()
                .map(extent -> Arrays.stream(getCellsByExtent(extent)))
                .flatMapToInt(x -> x)
                .distinct()
                .toArray()
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (cellMap.isEmpty() && !controller.getDataProvider().getExtents().isEmpty()) {
            controller.getDataProvider().getExtents()
                    .forEach(extent -> {
                        int[] cellIds = getCellsByExtent(extent);
                        for (int cellIdx : cellIds) {
                            cellMap.computeIfAbsent(cellIdx, index -> new LinkedList<>()).add(extent);
                        }
                    });
            repaint();
        }
        for (int row = 1; row <= MAP_HEIGHT; row++) {
            for (int col = 1; col <= MAP_WIDTH; col++) {
                int cellIdx = getCellIndex(row, col);
                if (!isCellNone(cellIdx))
                    paintCell(g, row-1, col-1, getCellColor(cellIdx));
            }
        }
    }

    private void paintCell(int cellIdx, Color color) {
        int row      = (int) Math.ceil((cellIdx-1) / MAP_WIDTH);
        int col      = (cellIdx-1) % MAP_WIDTH;
        paintCell(getGraphics(), row, col, color == null ? getCellColor(cellIdx) : color);
    }

    private void paintCell(Graphics g, int row, int col, Color color) {
        int topLeftX = col * CELL_SIZE - 1;
        int topLeftY = row * CELL_SIZE - 1;

        g.setColor(color);
        g.fillRect(topLeftX, topLeftY, CELL_SIZE, CELL_SIZE);

        g.setColor(Color.GRAY);
        g.drawRect(topLeftX, topLeftY, CELL_SIZE, CELL_SIZE);
    }

    private int getCellIndex(int row, int col) {
        return MAP_WIDTH * (row-1) + col;
    }

    private Color getCellColor(int cellIdx) {
        if (selectedCell == cellIdx) {
            return CELL_SEL;
        }
        boolean highlighted = highlightedCells != null && IntStream.of(highlightedCells).anyMatch(x -> x == cellIdx);
        if (highlighted) {
            return CELL_HIGH;
        }

        if (isCellWithProblem(cellIdx)) {
            return CELL_BAD;
        } else if (isCellUsed(cellIdx)) {
            int minCellBlock = (cellIdx-1)*getBlocksPerCell();
            int maxCellBlock = minCellBlock+getBlocksPerCell();

            long cellUsedBlocks = getExtentsByCell(cellIdx).parallelStream()
                    .mapToLong(extent ->
                            Math.min(extent.getLastBlock(), maxCellBlock) - Math.max(extent.getFirstBlock(), minCellBlock)
                    ).sum();
            float percent = Math.max((float) cellUsedBlocks / getBlocksPerCell(), 0.3f);
            return mixColors(CELL_FREE, CELL_USED, percent);
        } else if (isCellSystem(cellIdx)) {
            return CELL_SYS;
        } else if (isCellNone(cellIdx)) {
            return CELL_NONE;
        } else {
            return CELL_FREE;
        }
    }

    public Color mixColors(Color color1, Color color2, double percent) {
        int redPart   = (int) ((color2.getRed()   - color1.getRed())   * percent + color1.getRed());
        int greenPart = (int) ((color2.getGreen() - color1.getGreen()) * percent + color1.getGreen());
        int bluePart  = (int) ((color2.getBlue()  - color1.getBlue())  * percent + color1.getBlue());
        return new Color(redPart, greenPart, bluePart);
    }

    private boolean isCellWithProblem(int cellIdx) {
        return getExtentsByCell(cellIdx).parallelStream()
                .anyMatch(extent -> !extent.getSegment().problemsGetter().get().isEmpty());
    }

    private boolean isCellUsed(int cellIdx) {
        return !cellMap.getOrDefault(cellIdx, Collections.emptyList()).isEmpty();
    }

    private boolean isCellSystem(int cellIdx) {
        return cellIdx * getBlocksPerCell() <= 128;
    }

    private boolean isCellNone(int cellIdx) {
        return cellIdx > (controller.getTableSpace().getBlocks() / getBlocksPerCell())+1;
    }

    private void selectCell(int cellIdx) {
        int prevSelectedCell = selectedCell;
        selectedCell = cellIdx;
        if (prevSelectedCell > 0) {
            paintCell(prevSelectedCell, getCellColor(prevSelectedCell));
        }
        if (selectedCell > 0) {
            paintCell(selectedCell, CELL_SEL);
        }
        SwingUtilities.invokeLater(() -> controller.getCellView().showExtents(getExtentsByCell(selectedCell)));
    }

    private void highlightCells(int[] cellIds) {
        int[] prevHighlighted = highlightedCells;
        highlightedCells = cellIds;

        for (int cellId : prevHighlighted) {
            if (Arrays.stream(cellIds).noneMatch(i -> i == cellId)) {
                paintCell(cellId, getCellColor(cellId));
            }
        }
        for (int cellId : highlightedCells) {
            paintCell(cellId, cellId == selectedCell ? CELL_SEL : CELL_HIGH);
        }
    }

    private int[] getCellsByExtent(Extent extent) {
        int firstCellId = (int) Math.ceil((float) extent.getFirstBlock() / getBlocksPerCell());
        int lastCellId  = (int) Math.ceil((float) extent.getLastBlock()  / getBlocksPerCell());
        return IntStream.rangeClosed(firstCellId, lastCellId).toArray();
    }

    private List<Extent> getExtentsByCell(int cellIdx) {
        return cellMap.getOrDefault(cellIdx, Collections.emptyList());
    }
}
