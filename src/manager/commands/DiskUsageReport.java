package manager.commands;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.GeneralRenderer;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.IEditor;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.presentation.CommandPanel;
import codex.presentation.SelectorPresentation;
import codex.presentation.SelectorTableModel;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.AbstractTaskView;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.task.TaskManager;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
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
import manager.nodes.Offshoot;
import manager.nodes.Release;
import manager.nodes.Repository;
import manager.type.WCStatus;
import org.apache.commons.io.FileDeleteStrategy;

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
        
        dialog.setPreferredSize(new Dimension(800, 600));
        dialog.setResizable(false);
        TES.quietTask(buildTask);
        dialog.setVisible(true);
    }
    
    class BuildReport extends AbstractTask<Void> {
        
        final AtomicLong totalSize = new AtomicLong(0);
        final AtomicLong totalFree = new AtomicLong(0);
        final Map<String, SelectorTableModel> tableModelsMap = new HashMap<>();
        
        final JPanel content;
        final JLabel total = new JLabel(MessageFormat.format(
                Language.get(DiskUsageReport.class.getSimpleName(), "task@total"), 
                formatFileSize(totalSize.get()),
                formatFileSize(totalFree.get())
        )) {{
            setBorder(new EmptyBorder(5, 10, 5, 5));
        }};
        
        public BuildReport(JPanel content) {
            super(Language.get(DiskUsageReport.class.getSimpleName(), "task@title"));
            this.content = content;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(DiskUsageReport.class.getSimpleName(), "task@structure"));
            
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
            
            if (binDir.exists()) {
                Stream.of(binDir.listFiles()).forEach((repositoryDir) -> {
                    if (!structureMap.containsKey(repositoryDir.getName())) {
                        structureMap.put(repositoryDir.getName(), new LinkedList<>());
                    }

                    Integer repoId = repoIndex.entrySet().stream().filter((indexEntry) -> {
                        return indexEntry.getValue().equals(repositoryDir.getName());
                    }).findFirst().get().getKey();

                    Stream.of(repositoryDir.listFiles()).forEach((cacheDir) -> {
                        structureMap.get(repositoryDir.getName()).add(new Entry(EntryKind.Cache, repoId, cacheDir));
                    });
                });
            }
            
            if (srcDir.exists()) {
                Stream.of(srcDir.listFiles()).forEach((repositoryDir) -> {
                    if (!structureMap.containsKey(repositoryDir.getName())) {
                        structureMap.put(repositoryDir.getName(), new LinkedList<>());
                    }
                    
                    Integer repoId = repoIndex.entrySet().stream().filter((indexEntry) -> {
                        return indexEntry.getValue().equals(repositoryDir.getName());
                    }).findFirst().get().getKey();
                    
                    Stream.of(repositoryDir.listFiles()).forEach((offshootDir) -> {
                        structureMap.get(repositoryDir.getName()).add(new Entry(EntryKind.Sources, repoId, offshootDir));
                    });
                });
            }
            
            Supplier<Stream<Map.Entry<String, List<Entry>>>> structureStream = () -> {
                return structureMap.entrySet().stream().sorted((o1, o2) -> {
                    Integer repoId1 = repoIndex.entrySet().stream().filter((indexEntry) -> {
                        return indexEntry.getValue().equals(o1.getKey());
                    }).findFirst().get().getKey();
                    Integer repoId2 = repoIndex.entrySet().stream().filter((indexEntry) -> {
                        return indexEntry.getValue().equals(o2.getKey());
                    }).findFirst().get().getKey();

                    Integer repoSeq1 = Integer.valueOf(CAS.readClassInstance(Repository.class, repoId1).get(EntityModel.SEQ));
                    Integer repoSeq2 = Integer.valueOf(CAS.readClassInstance(Repository.class, repoId2).get(EntityModel.SEQ));

                    return Integer.compare(repoSeq1, repoSeq2); 
                });
            };
            
            structureStream.get().forEach((repoEntry) -> {
                // Create tab
                JPanel repoContent = new JPanel(new BorderLayout());
                repoContent.setBackground(Color.WHITE);
                tabPanel.addTab(null, ImageUtils.resize(ImageUtils.getByPath("/images/repository.png"), 20, 20),  repoContent);
                
                // Create table model
                RepoEntity repoEntity = new RepoEntity();
                Entity prototype = null;
                try {
                    prototype = (Entity) EntryEntity.class.getConstructor(DiskUsageReport.class, Entry.class).newInstance(DiskUsageReport.this, null);
                } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                
                SelectorTableModel tableModel = new SelectorTableModel(repoEntity, prototype) {
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

                table.getColumnModel().getColumn(0).setPreferredWidth(130);
                table.getColumnModel().getColumn(0).setMaxWidth(130);
                table.getColumnModel().getColumn(1).setPreferredWidth(100);
                table.getColumnModel().getColumn(1).setMaxWidth(100);
                table.getColumnModel().getColumn(3).setPreferredWidth(100);
                table.getColumnModel().getColumn(3).setMaxWidth(100);
                
                JScrollPane scrollPane = new JScrollPane(table);
                
                // Create Tool bar and delete command
                CommandPanel toolBar = new CommandPanel();
                EntityCommand delete = new EntityCommand(
                        "delete", 
                        Language.get(DiskUsageReport.class.getSimpleName(), "delete@title"), 
                        ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 28, 28), 
                        Language.get(DiskUsageReport.class.getSimpleName(), "delete@title"), 
                        (entity) -> {
                            return 
                                    entity.model.getValue("kind") != EntryKind.Sources &&
                                    entity.model.getValue("size") != null;
                        }
                ) {
                    @Override
                    public boolean multiContextAllowed() {
                        return true;
                    }

                    @Override
                    public void actionPerformed(ActionEvent event) {
                        SwingUtilities.invokeLater(() -> {
                            String message; 
                            if (getContext().length == 1) {
                                message = MessageFormat.format(
                                        Language.get(
                                                SelectorPresentation.class.getSimpleName(), 
                                                "confirm@del.single"
                                        ), 
                                        getContext()[0]
                                );
                            } else {
                                StringBuilder msgBuilder = new StringBuilder(
                                        Language.get(
                                                SelectorPresentation.class.getSimpleName(), 
                                                "confirm@del.range"
                                        )
                                );
                                Arrays.asList(getContext()).forEach((entity) -> {
                                    msgBuilder.append("<br>&emsp;&#9913&nbsp;&nbsp;").append(entity.toString());
                                });
                                message = msgBuilder.toString();
                            }
                            MessageBox.show(
                                    MessageType.CONFIRMATION, null, message,
                                    (close) -> {
                                        if (close.getID() == Dialog.OK) {
                                            Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
                                            for (Entity entity : getContext()) {
                                                execute(entity, null);
                                            }
                                            activate();
                                        }
                                    }
                            );
                        });
                    }
                    
                    @Override
                    public void execute(Entity entity, Map<String, IComplexType> map) {
                        long sizeBefore = Long.valueOf((String) entity.model.getValue("bytes"));
                        
                        executeTask(entity, new DeleteTask(entity) {
                            @Override
                            public void finished(Void t) {
                                SwingUtilities.invokeLater(() -> {
                                    File directory = new File((String) entity.model.getValue("file"));
                                    long sizeAfter = Long.valueOf((String) entity.model.getValue("bytes"));
                                    
                                    if (!directory.exists()) {
                                        tableModelsMap.get(repoEntry.getKey()).removeRow(
                                                repoEntity.getIndex(entity)
                                        );
                                        repoEntity.delete(entity);
                                    } else {
                                        entity.model.updateDynamicProp("size");
                                    }
                                    long deleted = sizeBefore - sizeAfter;
                                    totalSize.addAndGet(-1 * deleted);
                                    if (entity.model.getValue("used") == Boolean.FALSE) {
                                        totalFree.addAndGet(-1 * deleted);
                                    }
                                    total.setText(MessageFormat.format(
                                            Language.get(DiskUsageReport.class.getSimpleName(), "task@total"), 
                                            formatFileSize(totalSize.get()),
                                            formatFileSize(totalFree.get())
                                    ));
                                });
                            }
                            
                        }, true);
                    }
                };
                delete.activate();
                addListener(new ITaskListener() {
                    @Override
                    public void statusChanged(ITask task, Status status) {
                        delete.activate();
                    }
                });
                toolBar.addCommands(delete);
                
                table.getSelectionModel().addListSelectionListener((event) -> {
                    if (event.getValueIsAdjusting()) return;
                    List<Entity> context = Arrays
                        .stream(table.getSelectedRows())
                        .boxed()
                        .map((rowIdx) -> {
                            return tableModel.getEntityAt(rowIdx);
                        })
                        .collect(Collectors.toList());
                    delete.setContext(context.toArray(new Entity[]{}));
                });
                
                JLabel repoName = new JLabel(MessageFormat.format(
                        Language.get(DiskUsageReport.class.getSimpleName(), "task@repo"), 
                        repoEntry.getKey())) 
                {{
                    setOpaque(true);
                    setBorder(new EmptyBorder(3, 5, 3, 0));
                    setBackground(Color.WHITE);
                }};
                JPanel header = new JPanel(new BorderLayout());
                header.add(repoName, BorderLayout.NORTH);
                header.add(toolBar, BorderLayout.CENTER);
                repoContent.add(header, BorderLayout.NORTH);
                repoContent.add(scrollPane, BorderLayout.CENTER);
                
                // Create table rows
                repoEntry.getValue().forEach((entry) -> {
                    
                    EntryEntity newEntity = new EntryEntity(entry);
                    repoEntity.insert(newEntity);
                    
                    if (entry.kind == EntryKind.Cache || entry.kind == EntryKind.Sources) {
                        StringJoiner starterPath = new StringJoiner(File.separator);
                        starterPath.add(entry.dir.getAbsolutePath());
                        starterPath.add("org.radixware");
                        starterPath.add("kernel");
                        starterPath.add("starter");
                        starterPath.add("bin");
                        starterPath.add("dist");
                        starterPath.add("starter.jar");
                        File starter = new File(starterPath.toString());
                        if (starter.exists() && !starter.renameTo(starter)) {
                            try {
                                newEntity.getLock().acquire();
                            } catch (InterruptedException e) {}
                        }
                    }
                    
                    Entity easEntity = entry.getEntity();
                    List<IConfigStoreService.ForeignLink> links;
                    if (easEntity != null) {
                        links = CAS.findReferencedEntries(
                                easEntity.getClass(), 
                                easEntity.model.getID()
                        );
                        if (easEntity.islocked()) {
                            try {
                                newEntity.getLock().acquire();
                            } catch (InterruptedException e) {}
                        }
                    } else {
                        links = new LinkedList<>();
                    }
                    newEntity.model.setValue("used", !links.isEmpty());
                    
                    newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                        final int entityIdx = repoEntity.getIndex(newEntity);
                        if (newEntity.model.getProperties(Access.Select).contains(name)) {
                            tableModel.setValueAt(newValue, entityIdx, newEntity.model.getProperties(Access.Select).indexOf(name));
                        }
                    });
                    
                    tableModel.addRow(
                            newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                switch (propName) {
                                    case "name":
                                        switch (entry.kind) {
                                            case Sources:
                                                return new Iconified() {
                                                    @Override
                                                    public ImageIcon getIcon() {
                                                        WCStatus  wcStatus = ((Offshoot) easEntity).getStatus();
                                                        Iconified builtResult = (Iconified) easEntity.model.getValue("built");
                                                        return wcStatus != WCStatus.Succesfull ? wcStatus.getIcon() : (
                                                                builtResult == null ? WCStatus.Absent.getIcon() : builtResult.getIcon()
                                                        );
                                                    }

                                                    @Override
                                                    public String toString() {
                                                        return entry.dir.getName();
                                                    }
                                                };
                                            default:
                                                return entry.dir.getName();
                                        }
                                    case "used":
                                        switch (entry.kind) {
                                            case Cache:
                                            case Sources:
                                                return links.isEmpty() ? null : new Iconified() {
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
                                                };
                                            default:
                                                return null;
                                        }
                                    default:
                                        return newEntity.model.getValue(propName);
                                }
                            }).toArray()
                    );
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
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("Working directory usage report\n");
            AtomicLong processed = new AtomicLong(0);
            structureStream.get().forEach((repoEntry) -> {
                
                logBuilder.append("                     [").append(repoEntry.getKey()).append("]\n");
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
                        Entity entryEntity = tableModelsMap.get(repoEntry.getKey()).getEntityAt(repoEntry.getValue().indexOf(entry));
                        entryEntity.model.setValue("bytes", dirSize.get()+"");
                        entryEntity.model.updateDynamicProp("size");
                        
                        totalSize.addAndGet(dirSize.get());
                        if (entryEntity.model.getValue("used") == Boolean.FALSE) {
                            totalFree.addAndGet(dirSize.get());
                        }
                        logBuilder
                                .append("                     - ")
                                .append(entryEntity.model.getValue("used") == Boolean.TRUE ? "(+) " : "(-) ")
                                .append(entry.dir.getName())
                                .append(" / Type: ")
                                .append(entry.kind.title)
                                .append(" / Size: ")
                                .append(formatFileSize(dirSize.get()))
                                .append("\n");
                        total.setText(MessageFormat.format(
                                Language.get(DiskUsageReport.class.getSimpleName(), "task@total"), 
                                formatFileSize(totalSize.get()),
                                formatFileSize(totalFree.get())
                        ));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            });
            if (!isCancelled()) {
                logBuilder.append("                     Total: ").append(formatFileSize(totalSize.get()));
                Logger.getLogger().info(logBuilder);
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
    
    private enum  EntryKind implements Iconified {

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
    
    private class Entry {
        
        final EntryKind kind;
        final Integer   repoId;
        final File      dir;

        public Entry(EntryKind kind, Integer repoId, File dir) {
            this.kind   = kind;
            this.repoId = repoId;
            this.dir    = dir;
        }
        
        public Entity getEntity() {
            if (kind == EntryKind.Cache || kind == EntryKind.Sources) {
                EntityRef ref = new EntityRef(kind == EntryKind.Sources ? Offshoot.class : Release.class);
                
                Map<String, String> dbValues = CAS.readClassInstance(
                        ref.getEntityClass(),
                        dir.getName(), 
                        repoId
                );
                
                if (dbValues.containsKey(EntityModel.ID)) {
                    Integer entityId = Integer.valueOf(dbValues.get(EntityModel.ID));
                    ref.valueOf(entityId.toString());
                    return ref.getValue();
                }
            }
            return null;
        }
        
    }
    
    private class RepoEntity extends Catalog {

        public RepoEntity() {
            super(null, null);
        }

        @Override
        public Class getChildClass() {
            return null;
        }
    
    }
    
    private class EntryEntity extends Catalog {
    
        public EntryEntity(Entry entry) {
            super(null, null, entry != null ? entry.dir.getName() : null, null);
            model.addDynamicProp("kind", new Enum(entry != null ? entry.kind : EntryKind.Cache), null, null);
            model.addDynamicProp("file", new Str(entry != null ? entry.dir.getAbsolutePath() : null), Access.Any, null);
            model.addDynamicProp("name", new Str(model.getPID()), null, null);
            model.addDynamicProp("used", new Bool(false), null, null);
            
            model.addDynamicProp("bytes", new Str(null), Access.Any, null);
            model.addDynamicProp("size",  new Str(null), null, () -> {
                if (model.getValue("bytes") == null) {
                    return null;
                } else {
                    return formatFileSize(Long.valueOf((String) model.getValue("bytes")));
                }
            });
        }
        
        @Override
        public Class getChildClass() {
            return null;
        }
    
    }
    
    private class DeleteTask extends AbstractTask<Void> {
        
        private final Entity     entity;
        private final AtomicLong remain  = new AtomicLong(0);
        private final AtomicLong deleted = new AtomicLong(0);

        public DeleteTask(Entity entity) {
            super(Language.get(DiskUsageReport.class.getSimpleName(), "delete@title")+": "+entity.model.getValue("file"));
            this.entity = entity;
            this.remain.addAndGet(Long.valueOf((String) entity.model.getValue("bytes")));
        }

        @Override
        public Void execute() throws Exception {
            File directory = new File((String) entity.model.getValue("file"));
            
            setProgress(0, Language.get(DiskUsageReport.class.getSimpleName(), "delete@calc"));
            long totalFiles = Files.walk(directory.toPath()).count();

            AtomicInteger processed = new AtomicInteger(0);
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    deleted.addAndGet(attrs.size());
                    return processPath(file);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return processPath(dir);
                }
                
                private FileVisitResult processPath(Path path) {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    try {
                        FileDeleteStrategy.NORMAL.delete(path.toFile());
                        processed.addAndGet(1);
                        String fileName = path.toString().replace(directory.toPath()+File.separator, "");
                        setProgress(
                                (int) (processed.get() * 100 / totalFiles),
                                MessageFormat.format(
                                        Language.get(DiskUsageReport.class.getSimpleName(), "delete@progress"),
                                        fileName.replace(directory.toString(), "")
                                )
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            entity.model.setValue("bytes", (remain.get() - deleted.get())+"");
            
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(directory.getPath()).getParent());) {
                if (!dirStream.iterator().hasNext()) {
                    directory.getParentFile().delete();
                }
            }
            return null;
        }

        @Override
        public void finished(Void t) {}
    
    }
}
