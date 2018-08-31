package manager.commands;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.IEditor;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.AbstractTaskView;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import manager.nodes.Offshoot;
import manager.nodes.Release;
import manager.nodes.Repository;

public class DiskUsageReport extends EntityCommand {
    
    private final static IConfigStoreService    CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private static final IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    private static final ITaskExecutorService   TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public DiskUsageReport() {
        
        super(
                "usage", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/disk_usage.png"), 28, 28), 
                Language.get("title"), 
                (entity) -> entity.model.getValue("workDir") != null
        );
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        JPanel report  = new JPanel(new BorderLayout()) {{
            setBackground(Color.WHITE);
            setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new LineBorder(Color.LIGHT_GRAY, 1)
            ));
        }};
        
        BuildReport buildTask = new BuildReport(report);
        AbstractTaskView taskView = buildTask.createView(null);
        
        taskView.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 0, 5),
                new CompoundBorder(
                        new LineBorder(Color.LIGHT_GRAY, 1), 
                        new EmptyBorder(5, 5, 5, 5)
                )
        ));
                
        Dialog dialog = new Dialog(
                SwingUtilities.getWindowAncestor((JComponent) getButton()),
                (ImageIcon) getButton().getIcon(), 
                Language.get("title"), 
                new JPanel(new BorderLayout()) {
                    {
                        add(taskView, BorderLayout.NORTH);
                        add(report, BorderLayout.CENTER);
                    }
                }, 
                (event) -> {
                    if (event.getID() == Dialog.CLOSE) {
                        buildTask.cancel(true);
                    }
                }, 
                Dialog.Default.BTN_CLOSE
        );
        report.addHierarchyBoundsListener(new HierarchyBoundsListener() {
            @Override
            public void ancestorMoved(HierarchyEvent e) {
                dialog.pack();
            }

            @Override
            public void ancestorResized(HierarchyEvent e) {
                dialog.pack();
            }
        });
        
        dialog.setPreferredSize(new Dimension(700, 600));
        dialog.setResizable(false);
        TES.quietTask(buildTask);
        dialog.setVisible(true);
    }
    
    public class BuildReport extends AbstractTask<Void> {

        private final JPanel content;
        
        public BuildReport(JPanel content) {
            super(Language.get(DiskUsageReport.class.getSimpleName(), "task@title"));
            this.content = content;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(DiskUsageReport.class.getSimpleName(), "task@structure"));
            
            JLabel total = new JLabel(MessageFormat.format(
                    Language.get(DiskUsageReport.class.getSimpleName(), "task@total"), 
                    formatFileSize(0),
                    formatFileSize(0)
            ));
            total.setBorder(new EmptyBorder(5, 10, 5, 5));
            content.add(total, BorderLayout.NORTH);
            
            JTabbedPane tabPanel = new JTabbedPane(JTabbedPane.LEFT) {{
                setFocusable(false);
                setBorder(new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
            }};
            content.add(tabPanel, BorderLayout.CENTER);
            
            final File workDir = new File(EAS.getRoot().model.getValue("workDir").toString());
            final File srcDir  = new File(workDir, "sources");
            final File binDir  = new File(workDir, "releases");
            
            Map<Integer, String> repoIndex = CAS.readCatalogEntries(null, Repository.class)
                    .entrySet()
                    .stream()
                    .map((entry) -> {
                        entry.setValue(Repository.urlToDirName(CAS.readClassInstance(Repository.class, entry.getKey()).get("repoUrl")));
                        return entry;
                    })
                    .collect(Collectors.toMap(
                        entry -> entry.getKey(), 
                        entry -> entry.getValue()
                    ));
            
            final Map<String, List<Entry>> structureMap = new LinkedHashMap<>();
            if (srcDir.exists()) {
                Stream.of(srcDir.listFiles()).forEach((repositoryDir) -> {
                    if (!structureMap.containsKey(repositoryDir.getName())) {
                        structureMap.put(repositoryDir.getName(), new LinkedList<>());
                    }
                    Stream.of(repositoryDir.listFiles()).forEach((offshootDir) -> {
                        structureMap.get(repositoryDir.getName()).add(new Entry(EntryKind.Sources, offshootDir));
                    });
                });
            }
            if (binDir.exists()) {
                Stream.of(binDir.listFiles()).forEach((repositoryDir) -> {
                    if (!structureMap.containsKey(repositoryDir.getName())) {
                        structureMap.put(repositoryDir.getName(), new LinkedList<>());
                    }
                    
                    Stream.of(repositoryDir.listFiles()).forEach((cacheDir) -> {
                        structureMap.get(repositoryDir.getName()).add(new Entry(EntryKind.Cache, cacheDir));
                    });
                });
            }
            Map<String, DefaultTableModel> tableModelsMap = new HashMap<>();

            structureMap.entrySet().stream().forEach((repoEntry) -> {
                // Create tab
                JPanel repoContent = new JPanel(new BorderLayout());
                repoContent.setBackground(Color.WHITE);
                tabPanel.addTab(null, ImageUtils.resize(ImageUtils.getByPath("/images/repository.png"), 20, 20),  repoContent);
                
                repoContent.add(new JLabel(MessageFormat.format(Language.get(DiskUsageReport.class.getSimpleName(), "task@repo"), 
                            repoEntry.getKey()
                        )) {{
                            setOpaque(true);
                            setBorder(new EmptyBorder(3, 5, 3, 0));
                            setBackground(Color.decode("#EEEEEE"));
                        }}, 
                        BorderLayout.NORTH
                );
                
                // Create table model
                DefaultTableModel tableModel = new DefaultTableModel() {
                    {
                        addColumn(Language.get(DiskUsageReport.class.getSimpleName(), "entry@type")); 
                        addColumn(Language.get(DiskUsageReport.class.getSimpleName(), "entry@name"));
                        addColumn(Language.get(DiskUsageReport.class.getSimpleName(), "entry@used"));
                        addColumn(Language.get(DiskUsageReport.class.getSimpleName(), "entry@size"));
                    }
                    @Override
                    public Class<?> getColumnClass(int columnIndex) {
                        return Str.class;
                    }
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
                tableModelsMap.put(repoEntry.getKey(), tableModel);
                
                // Create table
                JTable table = new JTable(tableModel);
                table.setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
                table.setShowVerticalLines(false);
                table.setIntercellSpacing(new Dimension(0,0));

                table.setDefaultRenderer(Str.class, new GeneralRenderer());
                table.getTableHeader().setDefaultRenderer(new GeneralRenderer());                    

                table.getColumnModel().getColumn(0).setPreferredWidth(110);
                table.getColumnModel().getColumn(0).setMaxWidth(110);
                table.getColumnModel().getColumn(1).setPreferredWidth(100);
                table.getColumnModel().getColumn(1).setMaxWidth(100);
                table.getColumnModel().getColumn(3).setMaxWidth(80);
                
                JScrollPane threadScrollPane = new JScrollPane(table);
                repoContent.add(threadScrollPane, BorderLayout.CENTER);
                
                // Create table rows
                repoEntry.getValue().forEach((entry) -> {
                    
                    List<IConfigStoreService.ForeignLink> links;
                    switch (entry.kind) {
                        case Cache:
                        case Sources:
                            Integer repoId = repoIndex.entrySet().stream().filter((indexEntry) -> {
                                return indexEntry.getValue().equals(repoEntry.getKey());
                            }).findFirst().get().getKey();
                            
                            Map<String, String> dbValues =  CAS.readClassInstance(
                                    entry.kind == EntryKind.Sources ? Offshoot.class : Release.class, 
                                    entry.dir.getName(), 
                                    repoId
                            );
                            if (dbValues.containsKey(EntityModel.ID)) {
                                Integer entityId = Integer.valueOf(dbValues.get(EntityModel.ID));
                                links = CAS.findReferencedEntries(
                                        entry.kind == EntryKind.Sources ? Offshoot.class : Release.class, 
                                        entityId
                                );
                            } else {
                                links = new LinkedList<>();
                            }                            
                            break;
                        default:
                            links = new LinkedList<>();
                    }
                    
                    tableModel.addRow(new Object[]{
                        entry.kind,
                        entry.kind == EntryKind.Sources ? 
                                new Iconified() {
                                    @Override
                                    public ImageIcon getIcon() {
                                        return Offshoot.getStatus(entry.dir).getIcon();
                                    }

                                    @Override
                                    public String toString() {
                                        return entry.dir.getName();
                                    }
                                } : 
                                entry.dir.getName(),
                        links.isEmpty() ? null : new Iconified() {
                            @Override
                            public ImageIcon getIcon() {
                                try {
                                    return EAS.getEntity(Class.forName(links.get(0).entryClass), links.get(0).entryID).getIcon();
                                } catch (ClassNotFoundException e) {}
                                return null;
                            }

                            @Override
                            public String toString() {
                                return links.stream().map((link) -> {
                                    try {
                                        return EAS.getEntity(Class.forName(links.get(0).entryClass), links.get(0).entryID).toString();
                                    } catch (ClassNotFoundException e) {}
                                    return null;
                                }).collect(Collectors.joining(","));
                            }
                        },
                        ""
                    });
                });
            });
            
            // Calc total files number
            long totalFiles = structureMap.entrySet().parallelStream().mapToLong((repoEntry) -> {
                return repoEntry.getValue().parallelStream()
                        .filter((entry) -> {
                            return entry.kind == EntryKind.Cache || entry.kind == EntryKind.Sources;
                        }).mapToLong((entry) -> {
                            try {
                                return Files.walk(entry.dir.toPath()).count();
                            } catch (IOException e) {
                                return 0;
                            }
                        }).sum();
            }).sum();
            
            // Calc entries size
            AtomicLong totalSize = new AtomicLong(0);
            AtomicLong totalFree = new AtomicLong(0);
            AtomicLong processed = new AtomicLong(0);
            
            structureMap.entrySet().stream().forEach((repoEntry) -> {
                repoEntry.getValue().stream().forEach((entry) -> {
                    AtomicLong dirSize = new AtomicLong(0);
                    if (isCancelled()) {
                        return;
                    }
                    setProgress(0, MessageFormat.format(
                            Language.get(DiskUsageReport.class.getSimpleName(), "task@process"), 
                            entry.dir.toPath()
                    ));
                    try {
                        Files.walkFileTree(entry.dir.toPath(), new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (isCancelled()) {
                                    return FileVisitResult.TERMINATE;
                                }
                                dirSize.addAndGet(attrs.size());
                                processed.addAndGet(1);
                                setProgress((int) (processed.get() * 100 / totalFiles), getDescription());
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                                if (isCancelled()) {
                                    return FileVisitResult.TERMINATE;
                                }
                                processed.addAndGet(1);
                                setProgress((int) (processed.get() * 100 / totalFiles), getDescription());
                                return FileVisitResult.CONTINUE;
                            }
                        });
                        totalSize.addAndGet(dirSize.get());
                        if (tableModelsMap.get(repoEntry.getKey()).getValueAt(repoEntry.getValue().indexOf(entry), 2) == null) {
                            totalFree.addAndGet(dirSize.get());
                        }
                        total.setText(MessageFormat.format(
                                Language.get(DiskUsageReport.class.getSimpleName(), "task@total"), 
                                formatFileSize(totalSize.get()),
                                formatFileSize(totalFree.get())
                        ));
                        tableModelsMap.get(repoEntry.getKey()).setValueAt(
                                formatFileSize(dirSize.get()), 
                                repoEntry.getValue().indexOf(entry),
                                3
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            });
            return null;
        }

        @Override
        public void finished(Void t) {}
    
    }
    
    static String formatFileSize(long size) {
        String hrSize;

        double bytes     = size;
        double kilobytes = size/1024.0;
        double megabytes = ((size/1024.0)/1024.0);
        double gigabytes = (((size/1024.0)/1024.0)/1024.0);
        double terabytes = ((((size/1024.0)/1024.0)/1024.0)/1024.0);

        DecimalFormat dec = new DecimalFormat("0.00");

        if (terabytes > 1) {
            hrSize = dec.format(terabytes).concat(" TB");
        } else if (gigabytes > 1) {
            hrSize = dec.format(gigabytes).concat(" GB");
        } else if (megabytes > 1) {
            hrSize = dec.format(megabytes).concat(" MB");
        } else if (kilobytes > 1) {
            hrSize = dec.format(kilobytes).concat(" KB");
        } else {
            hrSize = dec.format(bytes).concat(" Bytes");
        }
        return hrSize;
    }
    
    class Entry {
        
        final EntryKind kind;
        final File      dir;

        public Entry(EntryKind kind, File dir) {
            this.kind = kind;
            this.dir  = dir;
        }
        
    }
    
    enum EntryKind implements Iconified {

        Sources(Language.get(DiskUsageReport.class.getSimpleName(), "kind@sources"), ImageUtils.getByPath("/images/branch.png")),
        Cache(Language.get(DiskUsageReport.class.getSimpleName(), "kind@cache"), ImageUtils.getByPath("/images/release.png"));

        private final String    title;
        private final ImageIcon icon;

        private EntryKind(String title, ImageIcon icon) {
            this.title  = title;
            this.icon   = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }

    }
    
}
