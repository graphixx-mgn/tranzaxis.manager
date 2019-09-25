package manager.commands.common;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.common.report.*;
import manager.nodes.Common;
import manager.nodes.Repository;
import manager.nodes.RepositoryBranch;
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DiskUsageReport extends EntityCommand<Common> {

    private final static IConfigStoreService  CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    private static final String SIZE_FORMAT = Language.get(DiskUsageReport.class, "task@total");
    public  static final String TRASH       = "trash@title";

    public DiskUsageReport() {
        super(
                "usage",
                "title",
                ImageUtils.getByPath("/images/disk_usage.png"),
                Language.get("title"),
                (entity) -> entity.model.getValue("workDir") != null
        );
    }

    @Override
    public void execute(Common common, Map<String, IComplexType> map) {
        TES.executeTask(new BuildStructure());
    }

    class BuildStructure extends AbstractTask<List<RepoView>> {

        BuildStructure() {
            super(Language.get(DiskUsageReport.class, "task@structure"));
        }

        @Override
        public List<RepoView> execute() throws Exception {
            final File workDir = DiskUsageReport.this.getContext().get(0).getWorkDir().toFile();

            List<Entry> entries = StreamSupport.stream(ClassIndex.getSubclasses(RepositoryBranch.class).spliterator(), true)
                    .map(branchClass -> getBranchEntries(workDir, branchClass))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            return entries.stream()
                    .peek(entry -> entry.model.updateDynamicProps())
                    .collect(Collectors.groupingBy(
                            entry -> {
                                if (entry.getOwner() == null) {
                                    return Entity.newInstance(RepoView.class, null, TRASH);
                                } else {
                                    Repository repo = (Repository) entry.getOwner();
                                    return Entity.newInstance(RepoView.class, repo.toRef(), Repository.urlToDirName(repo.getRepoUrl()));
                                }
                            }
                    ))
                    .entrySet()
                    .stream()
                    .peek(repoEntries -> repoEntries.getValue().forEach(entry -> repoEntries.getKey().insert(entry)))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .peek(RepoView::sortChildren)
                    .collect(Collectors.toList());
        }

        List<Entry> getBranchEntries(File workDir, Class<? extends RepositoryBranch> branchClass) {
            final File localDir = new File(workDir, branchClass.getAnnotation(RepositoryBranch.Branch.class).localDir());
            Map<String, Repository> REPO_INDEX = CAS.readCatalogEntries(null, Repository.class)
                    .entrySet().stream()
                    .map((entry) -> EntityRef.build(Repository.class, entry.getKey()).getValue()).collect(Collectors.toMap(
                            entry -> Repository.urlToDirName(entry.getRepoUrl()),
                            entry -> entry
                    ));

            if (localDir.exists()) {
                return Stream.of(IComplexType.coalesce(localDir.listFiles(), new File[]{}))
                        .map(file -> {
                            if (REPO_INDEX.containsKey(file.getName())) {
                                final File repoDir = file;
                                final Repository repository = REPO_INDEX.get(repoDir.getName());
                                final RepositoryBranch branch = Entity.newInstance(branchClass, repository.toRef(), null);

                                Collection<String> PIDs = branch.getChildrenPIDs();

                                return Stream.of(IComplexType.coalesce(repoDir.listFiles(), new File[]{}))
                                        .map(repoBoundFile -> {
                                            if (repoBoundFile.isFile()) {
                                                return new FileEntry(repository.toRef(), repoBoundFile.getAbsolutePath());
                                            } else {
                                                if (PIDs.contains(repoBoundFile.getName())) {
                                                    Class<? extends Entry> entryClass = StreamSupport.stream(ClassIndex.getSubclasses(Entry.class).spliterator(), false)
                                                            .filter(aClass -> aClass.getAnnotation(BranchLink.class).branchCatalogClass().equals(branchClass))
                                                            .findAny().orElse(DirEntry.class);
                                                    return Entity.newInstance(entryClass, repository.toRef(), repoBoundFile.getAbsolutePath());
                                                } else {
                                                    return new DirEntry(repository.toRef(), repoBoundFile.getAbsolutePath());
                                                }
                                            }
                                        })
                                        .collect(Collectors.toList());
                            } else {
                                if (file.isDirectory()) {
                                    return Collections.singletonList(new DirEntry(null, file.getAbsolutePath()));
                                } else {
                                    return Collections.singletonList(new FileEntry(null, file.getAbsolutePath()));
                                }
                            }
                        })
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        }

        private JPanel createView(List<RepoView> repoEntities) {
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

        private JPanel createRepoView(RepoView repoEntity) {
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

        @Override
        public void finished(List<RepoView> result) {
            if (isCancelled() || isFailed()) {
                // Return
            } else if (result.isEmpty()) {
                MessageBox.show(MessageType.INFORMATION, Language.get(DiskUsageReport.class, "task@empty"));
            } else {
                AtomicLong totalSize  = new AtomicLong(0);
                AtomicLong unusedSize = new AtomicLong(0);

                final JLabel sizeInfo = new JLabel(
                        MessageFormat.format(
                                SIZE_FORMAT,
                                FileUtils.formatFileSize(0),
                                FileUtils.formatFileSize(0)
                        )
                ) {{
                    setIcon(ImageUtils.getByPath("/images/info.png"));
                    setIconTextGap(10);
                    setBorder(new EmptyBorder(5, 10, 0, 0));
                }};

                ICalcListener listener = (entry, value) -> {
                    totalSize.addAndGet(value);
                    if (!entry.isUsed()) {
                        unusedSize.addAndGet(value);
                    }
                    sizeInfo.setText(MessageFormat.format(
                            SIZE_FORMAT,
                            FileUtils.formatFileSize(totalSize.get()),
                            FileUtils.formatFileSize(unusedSize.get())
                    ));
                };

                CalculateDirsSize calcTask = new CalculateDirsSize(result, listener);
                AbstractTaskView taskView = calcTask.createView(null);
                taskView.setBorder(new CompoundBorder(
                        new EmptyBorder(5, 5, 0, 5),
                        new CompoundBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1),
                                new EmptyBorder(5, 5, 5, 5)
                        )
                ));

                result.parallelStream().forEach((repoEntity) -> {
                    repoEntity.lockEntries();
                    repoEntity.addNodeListener(new INodeListener() {
                        @Override
                        public void childDeleted(INode parentNode, INode childNode, int index) {
                            Entry entry = (Entry) childNode;
                            listener.sizeChanged(entry, -1*entry.getOriginalSize());
                        }
                    });
                    repoEntity.childrenList().parallelStream().forEach((node) -> node.addNodeListener(new INodeListener() {
                        @Override
                        public void childChanged(INode node) {
                            final Entry entry = (Entry) node;
                            listener.sizeChanged(entry, -1*(entry.getOriginalSize() - entry.getActualSize()));
                        }
                    }));
                });

                JPanel view = createView(result);
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
                            calcTask.cancel(true);
                            result.forEach(repoView -> repoView.model.remove());
                            result.forEach(RepoView::unlockEntries);
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
    }

    class CalculateDirsSize extends AbstractTask<Void> {

        private final List<RepoView>  repoEntities;
        private final ICalcListener listener;

        CalculateDirsSize(List<RepoView> repoEntities, ICalcListener listener) {
            super(Language.get(DiskUsageReport.class, "task@title"));
            this.repoEntities = repoEntities;
            this.listener = listener;
        }

        @Override
        public Void execute() {
            repoEntities.parallelStream().forEach((repoEntity) -> {
                repoEntity.childrenList().parallelStream().forEach((node) -> {
                    Entry entry = (Entry) node;
                    AtomicLong entrySize = new AtomicLong(0);
                    try {
                        Files.walkFileTree(new File(entry.getPID()).toPath(), new SimpleFileVisitor<Path>() {

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
                                if (entry.skipDirectory(dir)) {
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
                            listener.sizeChanged(entry, entrySize.get());
                            entry.setSize(entrySize.get());
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

    @FunctionalInterface
    interface ICalcListener {
        void sizeChanged(Entry entry, long value);
    }
}
