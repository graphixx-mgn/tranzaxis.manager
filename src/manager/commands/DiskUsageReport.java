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
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.AbstractTaskView;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.EntityRef;
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
import java.nio.file.Paths;
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

        private final Map<File, List<File>> sources = new LinkedHashMap<>();
        private final JPanel content;
        
        public BuildReport(JPanel content) {
            super(Language.get(DiskUsageReport.class.getSimpleName(), "task@title"));
            this.content = content;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(DiskUsageReport.class.getSimpleName(), "task@structure"));
            File workDir  = new File(EAS.getRoot().model.getValue("workDir").toString());
            File versions = new File(workDir, "sources");
            
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
            
            Map<String, DefaultTableModel> models = new HashMap<>();
            
            if (versions.exists()) {
                for (File repositoryDir : versions.listFiles()) {
                    JPanel repoContent = new JPanel(new BorderLayout());
                    repoContent.setBackground(Color.WHITE);
                    tabPanel.addTab(null, ImageUtils.resize(ImageUtils.getByPath("/images/repository.png"), 20, 20),  repoContent);
                    repoContent.add(new JLabel(MessageFormat.format(Language.get(DiskUsageReport.class.getSimpleName(), "task@repo"), 
                                    repositoryDir.getName()
                            )) {{
                                setBorder(new EmptyBorder(3, 5, 3, 0));
                            }}, 
                            BorderLayout.NORTH
                    );
                    
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
                    models.put(repositoryDir.getName(), tableModel);
                    
                    JTable table = new JTable(tableModel);
                    table.setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
                    table.setShowVerticalLines(false);
                    table.setIntercellSpacing(new Dimension(0,0));
                    
                    table.setDefaultRenderer(Str.class, new GeneralRenderer());
                    table.getTableHeader().setDefaultRenderer(new GeneralRenderer());
                    
                    table.getColumnModel().getColumn(0).setPreferredWidth(10);
                    table.getColumnModel().getColumn(3).setMaxWidth(80);

                    JScrollPane threadScrollPane = new JScrollPane(table);
                    repoContent.add(threadScrollPane, BorderLayout.CENTER);
                    
                    if (!sources.containsKey(repositoryDir)) {
                        sources.put(repositoryDir, new LinkedList<>());
                    }
                    
                    Map<Integer, String> PIDs = CAS.readCatalogEntries(null, Offshoot.class);
                    
                    for (File versionDir : repositoryDir.listFiles()) {
                        sources.get(repositoryDir).add(versionDir);
                        
                        List<IConfigStoreService.ForeignLink> links = CAS.findReferencedEntries(Offshoot.class, 
                                PIDs.entrySet().stream().filter((entry) -> {
                                    return entry.getValue().equals(versionDir.getName());
                                }).findFirst().get().getKey()
                        );
                        tableModel.addRow(new Object[]{
                            EntryKind.Offshoot, 
                            versionDir.getName(), 
                            links.isEmpty() ? null : new EntityRef(
                                    Class.forName(links.get(0).entryClass)) 
                                    {{
                                        valueOf(links.get(0).entryID.toString()); 
                                    }}.getValue(), 
                            ""
                        });
                    }
                }
            }
            long totalFiles = sources.entrySet().parallelStream().mapToLong((repositoryDir) -> {
                return repositoryDir.getValue().parallelStream().mapToLong((versionDir) -> {
                    try {
                        return Files.walk(Paths.get(versionDir.getPath())).count();
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            }).sum();
            
            AtomicLong totalSize = new AtomicLong(0);
            AtomicLong totalFree = new AtomicLong(0);
            AtomicLong processed = new AtomicLong(0);
            for (File repositoryDir : sources.keySet()) {
                for (File versionDir : sources.get(repositoryDir)) {
                    AtomicLong dirSize = new AtomicLong(0);
                    if (isCancelled()) {
                        return null;
                    }
                    setProgress(0, MessageFormat.format(
                            Language.get(DiskUsageReport.class.getSimpleName(), "task@process"), 
                            versionDir.toPath()
                    ));
                    Files.walkFileTree(versionDir.toPath(), new SimpleFileVisitor<Path>() {
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
                    if (models.get(repositoryDir.getName()).getValueAt(sources.get(repositoryDir).indexOf(versionDir), 2) == null) {
                        totalFree.addAndGet(dirSize.get());
                    }
                    
                    total.setText(MessageFormat.format(
                            Language.get(DiskUsageReport.class.getSimpleName(), "task@total"), 
                            formatFileSize(totalSize.get()),
                            formatFileSize(totalFree.get())
                    ));
                    models.get(repositoryDir.getName()).setValueAt(formatFileSize(dirSize.get()), sources.get(repositoryDir).indexOf(versionDir), 3);
                }
            }
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
    
    enum EntryKind implements Iconified {

        Offshoot(Language.get(DiskUsageReport.class.getSimpleName(), "kind@sources"), ImageUtils.getByPath("/images/branch.png"));

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
