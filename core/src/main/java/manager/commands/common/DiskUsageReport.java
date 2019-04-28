package manager.commands.common;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import manager.commands.offshoot.DeleteWC;
import manager.nodes.*;
import org.apache.commons.io.FileDeleteStrategy;
import org.atteo.classindex.ClassIndex;

public class DiskUsageReport extends EntityCommand<Common> {

    final static String TRASH = "trash@title";

    private final static IConfigStoreService  CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));
    
    private static final String SIZE_FORMAT = Language.get(DiskUsageReport.class, "task@total");
    
    private static final NotifableLong SIZE_TOTAL  = new NotifableLong(0);
    private static final NotifableLong SIZE_UNUSED = new NotifableLong(0);
    
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
    public void execute(Common common, Map<String, IComplexType> map) {
        TES.executeTask(new BuildStructure());
    }
    
    class BuildStructure extends AbstractTask<Map<String, List<Entry>>> {

        private Map<String, Repository> repoIndex = CAS.readCatalogEntries(null, Repository.class)
                .entrySet().stream()
                .map((entry) -> (Repository) EntityRef.build(Repository.class, entry.getKey()).getValue()).collect(Collectors.toMap(
                        entry -> Repository.urlToDirName(entry.getRepoUrl()),
                        entry -> entry
                ));

        BuildStructure() {
            super(Language.get(DiskUsageReport.class, "task@structure"));
        }

        @Override
        public Map<String, List<Entry>> execute() {
            final File workDir = DiskUsageReport.this.getContext().get(0).getWorkDir().toFile();
            
            final Map<String, List<Entry>> structureMap = new LinkedHashMap<>();

            ClassIndex.getSubclasses(RepositoryBranch.class).forEach(branchClass -> {
                buildBranchStructure(workDir, branchClass).entrySet().forEach(entry -> {
                    if (!structureMap.containsKey(entry.getKey())) {
                        structureMap.put(entry.getKey(), new LinkedList<>());
                    }
                    structureMap.get(entry.getKey()).addAll(entry.getValue());
                    structureMap.get(entry.getKey()).sort(Comparator.comparingInt(entry2 -> entry2.kind.ordinal()));
                });
            });
            List<String> repoOrder = new ArrayList<>(repoIndex.keySet());
            return structureMap.entrySet().stream()
                    .sorted(new Comparator<Map.Entry<String, List<Entry>>>() {
                        @Override
                        public int compare(Map.Entry<String, List<Entry>> o1, Map.Entry<String, List<Entry>> o2) {
                            return Integer.compare(repoOrder.indexOf(o1.getKey()), repoOrder.indexOf(o2.getKey()));
                        }
                    }).collect(Collectors.toMap(
                            Map.Entry::getKey, 
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, 
                            LinkedHashMap::new
                    ));
        }
        
        @Override
        public void finished(Map<String, List<Entry>> result) {
            if (isCancelled() || isFailed()) {
                // Return
            } else if (result.isEmpty()) {
                MessageBox.show(MessageType.INFORMATION, Language.get(DiskUsageReport.class, "task@empty"));
                
            } else {
                List<RepoEntity> repoEntities = result.entrySet().stream()
                        .map((entry) -> {
                            return buildRepoEntity(entry.getKey(), entry.getValue());
                        }).collect(Collectors.toList());
                
                CalculateDirsSize calcTask = new CalculateDirsSize(repoEntities);
                AbstractTaskView  taskView = calcTask.createView(null);
                taskView.setBorder(new CompoundBorder(
                        new EmptyBorder(5, 5, 0, 5),
                        new CompoundBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1), 
                                new EmptyBorder(5, 5, 5, 5)
                        )
                ));
                
                final JLabel sizeInfo = new JLabel(
                    MessageFormat.format(
                            SIZE_FORMAT, 
                            formatFileSize(0),
                            formatFileSize(0)
                    )
                ) {{
                    setIcon(ImageUtils.getByPath("/images/info.png"));
                    setIconTextGap(10);
                    setBorder(new EmptyBorder(5, 10, 0, 0));
                }};
                
                IChangeListener listener = (value) -> {
                    sizeInfo.setText(MessageFormat.format(
                            SIZE_FORMAT, 
                            formatFileSize(SIZE_TOTAL.get()),
                            formatFileSize(SIZE_UNUSED.get())
                    ));
                };
                
                SIZE_TOTAL.addListener(listener);
                SIZE_UNUSED.addListener(listener);
                
                JPanel view = createView(repoEntities);
                view.add(
                        new JPanel(new BorderLayout()){{
                            setBackground(Color.WHITE);
                            add(sizeInfo, BorderLayout.NORTH);
                            add(taskView, BorderLayout.CENTER);
                        }},
                        BorderLayout.NORTH
                );
                
                Dialog dialog = new Dialog(
                        null,
                        getIcon(),
                        Language.get(DiskUsageReport.class, "title"),
                        view,
                        (event) -> {
                            if (event.getID() == Dialog.EXIT || event.getID() == Dialog.CLOSE) {
                                calcTask.cancel(true);
                            }
                        },
                        Dialog.Default.BTN_CLOSE
                );
                TES.quietTask(calcTask);
                SwingUtilities.invokeLater(() -> {
                    dialog.setPreferredSize(new Dimension(800, 600));
                    dialog.setResizable(false);
                    dialog.setVisible(true);
                });
            }
        }

        private Map<String, List<Entry>> buildBranchStructure(File workDir, Class<? extends RepositoryBranch> branchClass) {
            Map<String, List<Entry>> branchEntries = new LinkedHashMap<>();

            File localDir = new File(workDir, branchClass.getAnnotation(RepositoryBranch.Branch.class).localDir());
            if (localDir.exists()) {
                Stream.of(IComplexType.coalesce(localDir.listFiles(), new File[]{})).forEach(directory -> {
                    if (repoIndex.containsKey(directory.getName())) {
                        RepositoryBranch branch = (RepositoryBranch) Entity.newInstance(branchClass, repoIndex.get(directory.getName()).toRef(), "title");
                        Collection<String> PIDs = branch.getChildrenPIDs();

                        Stream.of(IComplexType.coalesce(directory.listFiles(), new File[]{})).forEach(repoBoundDir -> {
                            String repoDirName = directory.getName();
                            if (!branchEntries.containsKey(repoDirName)) {
                                branchEntries.put(repoDirName, new LinkedList<>());
                            }

                            if (repoBoundDir.isFile()) {
                                branchEntries.get(repoDirName).add(new Entry(EntryKind.File, repoIndex.get(repoDirName), repoBoundDir));
                            } else {
                                if (PIDs.contains(repoBoundDir.getName())) {
                                    branchEntries.get(repoDirName).add(
                                            new Entry(EntryKind.fromClass(branch.getChildClass()), repoIndex.get(repoDirName), repoBoundDir)
                                    );
                                } else {
                                    branchEntries.get(repoDirName).add(
                                            new Entry(EntryKind.Dir, repoIndex.get(repoDirName), repoBoundDir)
                                    );
                                }
                                if (branchClass.equals(Development.class)) {
                                    StringJoiner logPath = new StringJoiner(File.separator);
                                    logPath.add(repoBoundDir.getAbsolutePath());
                                    logPath.add(".config");
                                    logPath.add("var");
                                    logPath.add("log");

                                    File logDir = new File(logPath.toString());
                                    if (logDir.exists()) {
                                        Stream.of(IComplexType.coalesce(logDir.listFiles(), new File[]{})).forEach(file -> {
                                            if (file.getName().startsWith("heapdump")) {
                                                branchEntries.get(repoDirName).add(
                                                        new Entry(EntryKind.Dump, repoIndex.get(repoDirName), file)
                                                );
                                            }
                                        });
                                    }
                                }
                            }
                        });
                    } else {
                        if (!branchEntries.containsKey(TRASH)) {
                            branchEntries.put(TRASH, new LinkedList<>());
                        }
                        branchEntries.get(TRASH).add(
                                new Entry(directory.isDirectory() ? EntryKind.Dir : EntryKind.File, null, directory)
                        );
                    }
                });
            }
            return branchEntries;
        }

        private JPanel createView(List<RepoEntity> repoEntities) {
            JTabbedPane tabPanel = new JTabbedPane(JTabbedPane.LEFT) {{
                setFocusable(false);
                setBorder(new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
            }};
            repoEntities.forEach((repoEntity) -> {
                tabPanel.addTab(
                        null, 
                        ImageUtils.resize(repoEntity.getIcon(), 20, 20),
                        createRepoView(repoEntity)
                );
            });
            
            JPanel content  = new JPanel(new BorderLayout()) {{
                setBackground(Color.WHITE);
                setBorder(new CompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    new LineBorder(Color.LIGHT_GRAY, 1)
                ));
            }};
            content.add(tabPanel, BorderLayout.CENTER);
            return new JPanel(new BorderLayout()) {{
                add(content, BorderLayout.CENTER);
            }};
        }
        
        private JPanel createRepoView(RepoEntity repoEntity) {
            JLabel repoName = new JLabel(MessageFormat.format(
                    Language.get(DiskUsageReport.class, "task@repo"),
                    repoEntity.getPID().equals(TRASH) ? Language.get(DiskUsageReport.class, TRASH) : repoEntity.getPID()
            )) {{
                setOpaque(true);
                setBorder(new EmptyBorder(3, 5, 3, 0));
                setBackground(Color.WHITE);
            }};
            
            JPanel repoView = new JPanel(new BorderLayout()) {{
                setBackground(Color.WHITE);
            }};
            repoView.add(repoName, BorderLayout.NORTH);
            
            repoView.add(
                    repoEntity.getSelectorPresentation(),
                    BorderLayout.CENTER
            );
            return repoView;
        }
        
        private RepoEntity buildRepoEntity(String repoDirName, List<Entry> entries) {
            RepoEntity repoEntity = new RepoEntity(repoDirName);
            entries.forEach((entry) -> {
                EntryEntity entryEntity = new EntryEntity(null, entry.file.getAbsolutePath());
                entryEntity.setEntry(entry);
                repoEntity.insert(entryEntity);
            });
            return repoEntity;
        }
        
    }

    class CalculateDirsSize extends AbstractTask<Void> {
        
        private final List<RepoEntity> repoEntities;

        CalculateDirsSize(List<RepoEntity> repoEntities) {
            super(Language.get(DiskUsageReport.class, "task@title"));
            this.repoEntities = repoEntities;
        }

        @Override
        public Void execute() {
            final long totalFiles = repoEntities.parallelStream()
                    .mapToLong((repoEntity) -> {
                        return repoEntity.childrenList().parallelStream()
                                .mapToLong((node) -> {
                                    EntryEntity entryEntity = (EntryEntity) node;
                                    try {
                                        return Files.walk(entryEntity.entry.file.toPath()).count();
                                    } catch (IOException e) {
                                        return 0;
                                    }
                                }).sum();
                    }).sum();
            
            SIZE_TOTAL.set(0);
            SIZE_UNUSED.set(0);
            repoEntities.parallelStream().forEach((repoEntity) -> {
                repoEntity.childrenList().parallelStream().forEach((node) -> {
                    EntryEntity entryEntity = (EntryEntity) node;
                    AtomicLong  entrySize = new AtomicLong(0);
                    try {
                        Files.walkFileTree(entryEntity.entry.file.toPath(), new SimpleFileVisitor<Path>() {
                            
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (isCancelled()) {
                                    return FileVisitResult.TERMINATE;
                                }
                                entrySize.addAndGet(attrs.size());
                                return FileVisitResult.CONTINUE;
                            }
                            
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                if (isCancelled()) {
                                    return FileVisitResult.TERMINATE;
                                }
                                if (dir.toFile().getName().equals(".config")) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                return FileVisitResult.CONTINUE;
                            }
                            
                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                                if (isCancelled()) {
                                    return FileVisitResult.TERMINATE;
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                        if (!isCancelled()) {
                            SIZE_TOTAL.addAndNotify(entrySize.get());
                            if (entryEntity.getUsed() == null || entryEntity.getUsed().isEmpty()) {
                                SIZE_UNUSED.addAndNotify(entrySize.get());
                            }
                            entryEntity.setSize(entrySize.get());
                        }
                    } catch (IOException e) {
                        throw new Error(e.getMessage());
                    }
                });
            });
            return null;
        }

        @Override
        public void finished(Void result) {
            
        }
    
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
            hrSize = dec.format(bytes).concat(" B");
        }
        return hrSize;
    }

    enum EntryKind implements Iconified {

        None(
                Language.get(DiskUsageReport.class,"kind@none"),
                ImageUtils.getByPath("/images/clearval.png"),
                null,false
        ),
        File(
                Language.get(DiskUsageReport.class,"kind@file"),
                ImageUtils.getByPath("/images/unknown_file.png"),
                null,false
        ),
        Dump(
                Language.get(DiskUsageReport.class,"kind@dump"),
                ImageUtils.getByPath("/images/thread.png"),
                null,false
        ),
        Dir(
                Language.get(DiskUsageReport.class,"kind@dir"),
                ImageUtils.getByPath("/images/unknown_dir.png"),
                null,false
        ),
        Sources(
                Language.get(DiskUsageReport.class,"kind@sources"),
                ImageUtils.getByPath("/images/branch.png"),
                Offshoot.class,true
        ),
        Cache(Language.get(DiskUsageReport.class,"kind@cache"),
                ImageUtils.getByPath("/images/release.png"),
                Release.class,true
        );

        private final String    title;
        private final ImageIcon icon;
        private final boolean   used;
        final Class<? extends BinarySource> clazz;

        EntryKind(String title, ImageIcon icon, Class<? extends BinarySource> clazz, boolean used) {
            this.title = title;
            this.icon  = icon;
            this.used  = used;
            this.clazz = clazz;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }

        public boolean isUsed() {
            return used;
        }

        public static EntryKind fromClass(Class<? extends Entity> clazz) {
            for (EntryKind kind : EntryKind.values()) {
                if (kind.clazz != null && kind.clazz.equals(clazz)) {
                    return kind;
                }
            }
            return None;
        }
    }
    
    class Entry {
        final EntryKind  kind;
        final Repository repo;
        final File       file;

        Entry(EntryKind kind, Repository repo, File file) {
            this.kind = kind;
            this.repo = repo;
            this.file = file;
        }
    }
    
    @FunctionalInterface
    interface IChangeListener {
        void valueChanged(long value);
    }
    
    static class NotifableLong extends AtomicLong {
        private final List<IChangeListener> listeners = new LinkedList<>();

        public NotifableLong(long initValue) {
            super(initValue);
        }
        
        void addAndNotify(long value) {
            synchronized (this) {
                final long val = addAndGet(value);
                if (value != 0) {
                    new LinkedList<>(listeners).forEach((listener) -> {
                        listener.valueChanged(val);
                    });
                }
            }
        }
        
        void addListener(IChangeListener listener) {
            listeners.add(listener);
        }
    }
    
    static class DeleteDirectory extends AbstractTask<Void> {

        private final EntryEntity entryEntity;
        private final long initialSize;

        public DeleteDirectory(EntryEntity entryEntity) {
            super(Language.get(DiskUsageReport.class, "delete@title") + ": " + entryEntity.entry.file);
            this.entryEntity = entryEntity;
            this.initialSize = entryEntity.getSize();
        }

        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(DiskUsageReport.class, "delete@calc"));
            long totalFiles = entryEntity.getFilesCount();

            AtomicInteger processed = new AtomicInteger(0);
            Files.walkFileTree(entryEntity.entry.file.toPath(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    return processPath(file);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return processPath(dir);
                }

                private FileVisitResult processPath(Path path) {
                    checkPaused();
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    try {
                        FileDeleteStrategy.NORMAL.delete(path.toFile());
                        processed.addAndGet(1);
                        String fileName = path.toString().replace(entryEntity.entry.file.toPath() + File.separator, "");
                        setProgress(
                                (int) (processed.get() * 100 / totalFiles),
                                MessageFormat.format(
                                        Language.get(DiskUsageReport.class, "delete@progress"),
                                        fileName
                                )
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(entryEntity.entry.file.getParentFile().toPath())) {
                if (!dirStream.iterator().hasNext()) {
                    entryEntity.entry.file.getParentFile().delete();
                }
            }
            return null;
        }

        @Override
        public void finished(Void result) {
            long finalSize = entryEntity.getSize();
            long deleted = -1 * (initialSize - finalSize);
            SIZE_TOTAL.addAndNotify(deleted);
            if (entryEntity.getUsed() == null || entryEntity.getUsed().isEmpty()) {
                SIZE_UNUSED.addAndNotify(deleted);
            }
            if (isCancelled()) {
                entryEntity.setSize(finalSize);
            } else {
                if (!entryEntity.entry.file.exists()) {
                    entryEntity.getParent().delete(entryEntity);
                }
            }
        }
    }
    
    static void  deleteFile(EntryEntity entryEntity) {
        long initialSize = entryEntity.entry.file.length();
        try {
            FileDeleteStrategy.NORMAL.delete(entryEntity.entry.file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (!entryEntity.entry.file.exists()) {
                entryEntity.getParent().delete(entryEntity);
                SIZE_TOTAL.addAndNotify(-1*initialSize);
                SIZE_UNUSED.addAndNotify(-1*initialSize);
            }
        }
    }
    
    static ITask deleteSource(EntryEntity entryEntity) {
        long initialSize = entryEntity.getSize();
        Offshoot offshoot = (Offshoot) entryEntity.entityRef.getValue();
        return ((DeleteWC) offshoot.getCommand("clean")).new DeleteTask(offshoot) {
            @Override
            public void finished(Void result) {
                super.finished(result);
                long finalSize = entryEntity.getSize();
                long deleted   = -1*(initialSize-finalSize);
                SIZE_TOTAL.addAndNotify(deleted);
                SIZE_UNUSED.addAndNotify(deleted);
                
                if (isCancelled()) {
                    entryEntity.setSize(finalSize);
                } else {
                    if (!entryEntity.entry.file.exists()) {
                        entryEntity.getParent().delete(entryEntity);
                    }
                }
            }
        };
    }
}
