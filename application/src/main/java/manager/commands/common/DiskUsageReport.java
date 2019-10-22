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
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
        TES.quietTask(new BuildStructure());
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

                                Collection<String> PIDs = branch.getChildrenPIDs().get(branch.getChildClass());

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
                // Do nothing
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

                CalculateDirsSize calcTask = new CalculateDirsSize(result/*, listener*/);
                AbstractTaskView taskView = calcTask.createView(null);
                taskView.setBorder(new CompoundBorder(
                        new EmptyBorder(5, 5, 0, 5),
                        new CompoundBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1),
                                new EmptyBorder(5, 5, 5, 5)
                        )
                ));

                Entry.ISizeListener listener = (entry, oldSize, newSize) -> {
                    totalSize.addAndGet(-1*oldSize+newSize);
                    if (!entry.isUsed()) {
                        unusedSize.addAndGet(-1*oldSize+newSize);
                    }
                    sizeInfo.setText(MessageFormat.format(
                            SIZE_FORMAT,
                            FileUtils.formatFileSize(totalSize.get()),
                            FileUtils.formatFileSize(unusedSize.get())
                    ));
                };

                result.parallelStream().forEach((repoEntity) -> {

                    repoEntity.addNodeListener(new INodeListener() {
                        @Override
                        public void childDeleted(INode parentNode, INode childNode, int index) {
                            Entry entry = (Entry) childNode;
                            listener.sizeChanged(entry, entry.getSize(), 0);
                            entry.clearSizeListener();
                        }
                    });
                    repoEntity.childrenList().parallelStream().forEach((node) -> {
                        Entry entry = (Entry) node;
                        entry.setSizeListener(listener);
                    });
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

                SwingUtilities.invokeLater(() -> {
                    TES.quietTask(calcTask);
                    new Dialog(
                            Dialog.findNearestWindow(),
                            getIcon(),
                            Language.get(DiskUsageReport.class, "title"),
                            view,
                            (event) -> {
                                calcTask.cancel(false);
                                result.forEach(repoView -> {
                                    repoView.childrenList().forEach(iNode -> ((Entry) iNode).model.remove());
                                    repoView.model.remove();
                                });
                            },
                            Dialog.Default.BTN_CLOSE.newInstance()
                    ) {
                        {
                            setResizable(false);
                        }
                        @Override
                        public Dimension getPreferredSize() {
                            return new Dimension(800, 600);
                        }
                    }.setVisible(true);
                });
            }
        }
    }

    class CalculateDirsSize extends AbstractTask<Void> {

        private final List<RepoView> repoEntities;

        CalculateDirsSize(List<RepoView> repoEntities) {
            super(Language.get(DiskUsageReport.class, "task@title"));
            this.repoEntities = repoEntities;
        }

        @Override
        public Void execute() {
            try {
                Executors.newCachedThreadPool().invokeAll(
                    repoEntities.stream()
                        .map(repoView -> repoView.childrenList().stream())
                        .flatMap(x -> x)
                        .map(node -> (Callable<Void>) () -> {
                            Entry entry = (Entry) node;
                            try {
                                entry.getLock().acquire();
                                long entrySize = entry.getActualSize();
                                //listener.sizeChanged(entry, entrySize);
                                entry.setSize(entrySize);
                            } catch (IOException e) {
                                throw new Error(e.getMessage());
                            } catch (InterruptedException ignore) {
                                //
                            } finally {
                                entry.getLock().release();
                            }
                            return null;
                        })
                        .collect(Collectors.toList())
                );
            } catch (InterruptedException ignore) {
                //
            }
            return null;
        }

        @Override
        public void finished(Void result) {}
    }
}
