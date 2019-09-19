package manager.commands.environment;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.utils.Language;
import manager.nodes.Environment;
import manager.nodes.Release;
import manager.nodes.Repository;
import manager.svn.SVN;
import manager.xml.Directory;
import manager.xml.DirectoryDocument;
import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.XmlException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CheckCache extends AbstractTask<Void> {

    private static final String INDEX_FILE = ".layer.index";
    private final static ExecutorService EXECUTOR = Executors.newFixedThreadPool(5);
    private static final ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    private final Environment environment;
    private final ITask[] planningTasks;
    private final String  releasePath;

    CheckCache(Environment environment, ITask... planningTasks) {
        super(MessageFormat.format(
                Language.get(Release.class, "cache@check.title"),
                environment.getBinaries().getLocalPath()
        ));
        this.environment   = environment;
        this.planningTasks = planningTasks;
        this.releasePath   = environment.getBinaries().getLocalPath();
    }

    @Override
    public Void execute() throws Exception {
        Release release = (Release) environment.getBinaries();
        ISVNAuthenticationManager authMgr = release.getRepository().getAuthManager();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        try {
            SVN.checkConnection(release.getRepository().getRepoUrl(), authMgr);
        } catch (SVNException | IOException e) {
            errorRef.set(e);
        }

        String  topLayer   = environment.getLayerUri(false);

        Map<String, Path> requiredLayers = release.getRequiredLayers(topLayer, false);
        if (requiredLayers.values().stream().anyMatch(Objects::isNull) && errorRef.get() == null) {
            requiredLayers = release.getRequiredLayers(topLayer, true);
        }
        Optional<String> lostLayer = requiredLayers.entrySet().stream()
                .filter((entry) -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .findFirst();
        if (lostLayer.isPresent()) {
            MessageBox.show(
                    MessageType.WARNING,
                    Repository.formatErrorMessage(MessageFormat.format(Language.get(Release.class, "fail@load.layers"), lostLayer.get()), errorRef.get())
            );
            return null;
        }

        boolean indexCheckResult = requiredLayers.entrySet().parallelStream().allMatch(layerToPath -> {
            File indexFile = Paths.get(releasePath, layerToPath.getKey(), INDEX_FILE).toFile();
            if (indexFile.exists()) {
                try {
                    return FileUtils.readLines(indexFile, StandardCharsets.UTF_8)
                            .parallelStream()
                            .allMatch(path -> Files.exists(Paths.get(releasePath.concat(File.separator).concat(path))));
                } catch (IOException e) {
                    return false;
                }
            } else {
                return false;
            }
        });

        if (!indexCheckResult) {
            if (errorRef.get() != null) {
                MessageBox.show(
                        MessageType.WARNING,
                        Repository.formatErrorMessage(Language.get(Release.class, "fail@load.cache"), errorRef.get())
                );
                return null;
            }
            try {
                release.getLock().acquire();
                setProgress(0, Language.get(Release.class, "cache@check.index"));

                Map<String, String> layerToUrl = release.getLayerPaths(new LinkedList<>(requiredLayers.keySet()));
                Map<String, Map<Path, IndexEntryFile>> layerToIndex = requiredLayers.keySet().stream()
                        .collect(Collectors.toMap(
                                layerName -> layerName,
                                layerName -> new HashMap<>()
                        ));
                layerToUrl.forEach((layer, url) -> {
                    layerToIndex.get(layer).putAll(indexCache(
                            new HashMap<Path, String>() {{
                                put(
                                    Paths.get(releasePath, layer, "directory.xml"),
                                    url.concat("/directory.xml")
                                );
                            }},
                            authMgr
                    ));
                });

                boolean loaded = layerToIndex.values().stream()
                        .allMatch(
                                index -> index.entrySet().stream()
                                        .allMatch(indexEntry -> indexEntry.getValue().isLoaded())
                        );
                if (!loaded) {
                    List<Map.Entry<Path, IndexEntryFile>> absentEntries =
                            layerToIndex.values().stream()
                            .map(layerIndex -> layerIndex.entrySet().stream())
                            .flatMap(x -> x)
                            .filter(indexEntry -> !indexEntry.getValue().isLoaded())
                            .collect(Collectors.toList());
                    loadGaps(absentEntries);
                }

                layerToIndex.forEach((layer, index) -> {
                    try {
                        Files.write(
                                Paths.get(releasePath, layer, INDEX_FILE),
                                index.values().parallelStream()
                                        .map(indexEntry -> indexEntry.includes.stream())
                                        .flatMap(stringStream -> stringStream)
                                        .collect(Collectors.toList()),
                                StandardCharsets.UTF_8
                        );
                    } catch (IOException e) {
                        // Do nothing
                    }
                });

            } catch (InterruptedException e) {
                // Do nothing
            } finally {
                release.getLock().release();
            }
        }
        return null;
    }

    @Override
    public void finished(Void result) {
        if (!isFailed() && !isCancelled()) {
            Arrays.asList(planningTasks).forEach(TES::enqueueTask);
        }
    }

    private Map<Path, IndexEntryFile> indexCache(final Map<Path, String> mapLocalToRemote, final ISVNAuthenticationManager authMgr) {
        int   baseIndex = Paths.get(releasePath).getNameCount();
        final Map<Path, IndexEntryFile> index = new HashMap<>();
        List<Callable<Map.Entry<Path, String>>> callables = mapLocalToRemote.entrySet().parallelStream()
                .map(localToRemote -> (Callable<Map.Entry<Path, String>>) () -> {
                    if (localToRemote.getValue().endsWith(".xml")) {
                        if (!Files.exists(localToRemote.getKey().getParent())) {
                            SVN.export(
                                    localToRemote.getValue().substring(0, localToRemote.getValue().lastIndexOf("/")),
                                    localToRemote.getKey().getParent().toString(),
                                    authMgr,
                                    SVNDepth.FILES
                            );
                        }
                        if (!Files.exists(localToRemote.getKey())) {
                            SVN.export(
                                    localToRemote.getValue(),
                                    localToRemote.getKey().toString(),
                                    authMgr,
                                    SVNDepth.EMPTY
                            );
                        }
                    }
                    return localToRemote;
                })
                .collect(Collectors.toList());

        try {
            EXECUTOR.invokeAll(callables)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).forEach(loadedEntry -> {
                File loadedFile = loadedEntry.getKey().toFile();
                if (loadedFile.getName().endsWith(".xml")) {
                    index.put(
                            loadedEntry.getKey(),
                            new IndexEntryFile(
                                    loadedEntry.getKey().subpath(baseIndex, loadedEntry.getKey().getNameCount()).toString(),
                                    loadedEntry.getValue()
                            )
                    );
                    try {
                        DirectoryDocument dirXml = DirectoryDocument.Factory.parse(loadedFile);
                        index.putAll(indexCache(
                                getIncludes(dirXml).parallelStream()
                                        .collect(Collectors.toMap(
                                                fileName -> Paths.get(loadedEntry.getKey().getParent() + File.separator + fileName),
                                                fileName -> loadedEntry.getValue().substring(0, loadedEntry.getValue().lastIndexOf("/")).concat("/").concat(fileName)
                                        )),
                                authMgr
                        ));
                        getFiles(dirXml).stream() // ХЗ почему, но parallelStream() приводит к NPE
                                .forEach(fileName -> {
                                    String dirName = fileName.replaceAll("([^/]*)/.*", "$1");
                                    Path filePath = Paths.get(loadedEntry.getKey().getParent() + File.separator + fileName);
                                    Path dirPath = Paths.get(loadedEntry.getKey().getParent() + File.separator + dirName);

                                    if (dirName.equals(fileName)) {
                                        index.put(
                                                dirPath,
                                                new IndexEntryFile(
                                                        dirPath.subpath(baseIndex, dirPath.getNameCount()).toString(),
                                                        loadedEntry.getValue().substring(0, loadedEntry.getValue().lastIndexOf("/")).concat("/").concat(dirName)
                                                )
                                        );
                                    } else {
                                        if (!index.containsKey(dirPath)) {
                                            index.put(
                                                    dirPath,
                                                    new IndexEntryDir(
                                                            dirPath.subpath(baseIndex, dirPath.getNameCount()).toString(),
                                                            loadedEntry.getValue().substring(0, loadedEntry.getValue().lastIndexOf("/")).concat("/").concat(dirName)
                                                    )
                                            );
                                        }
                                        index.get(dirPath).addInclude(
                                                filePath.subpath(baseIndex, filePath.getNameCount()).toString()
                                        );
                                    }
                                });
                    } catch (IOException e) {
                        throw new IllegalStateException(e.getMessage());
                    } catch (XmlException e) {
                        // It's not an index file
                    }
                } else {
                    throw new IllegalStateException("Not an XML file");
                }
            });
        } catch (InterruptedException e) {
            throw new CancelException();
        }
        return index;
    }

    private void loadGaps(List<Map.Entry<Path, IndexEntryFile>> gapsList) {
        ISVNAuthenticationManager authMgr = environment.getBinaries().getRepository().getAuthManager();
        AtomicInteger current = new AtomicInteger(0);
        List<Callable<Boolean>> callables = gapsList.parallelStream().map(gap ->
                (Callable<Boolean>) () -> {
                    SVN.export(
                            gap.getValue().url,
                            releasePath.concat(File.separator).concat(gap.getValue().path),
                            authMgr,
                            null
                    );
                    current.addAndGet(1);
                    setProgress(
                            current.get() * 100 / gapsList.size(),
                            MessageFormat.format(
                                    Language.get(Release.class, "cache@task.progress"),
                                    gap.getValue().path
                            )
                    );
                    return true;
                }
        ).collect(Collectors.toList());

        try {
            EXECUTOR.invokeAll(callables);
        } catch (InterruptedException e) {
            throw new CancelException();
        }
    }

    private static List<String> getIncludes(DirectoryDocument dirXml) {
        List<String> includes = new LinkedList<>();
        if (dirXml.getDirectory().getIncludes() != null) {
            includes.addAll(Stream.of(dirXml.getDirectory().getIncludes().getIncludeArray()).parallel()
                    .map(Directory.Includes.Include::getFileName)
                    .filter(fileName -> fileName.endsWith(".xml"))
                    .collect(Collectors.toList())
            );
        }
        return includes;
    }

    private static List<String> getFiles(DirectoryDocument dirXml) {
        List<String> files = new LinkedList<>();
        if (dirXml.getDirectory().getFileGroups() != null) {
            files.addAll(Stream.of(dirXml.getDirectory().getFileGroups().getFileGroupArray())
                    .parallel()
                    .map((fileGroup) -> Stream.of(fileGroup.getFileArray())
                            .filter((file) ->
                                    !file.getName().contains("/")   ||
                                            file.getName().endsWith(".jar") ||
                                            file.getName().endsWith(".war") ||
                                            file.getName().endsWith(".so")  ||
                                            file.getName().endsWith(".dll")
                            )
                            .map(Directory.FileGroups.FileGroup.File::getName)
                    )
                    .flatMap(x-> x)
                    .collect(Collectors.toList())
            );
        }
        return files;
    }

    public class IndexEntryFile {
        private   final String releasePath = environment.getBinaries().getLocalPath().concat(File.separator);
        protected final String path, url;
        protected final List<String> includes = new LinkedList<>();

        IndexEntryFile(String path, String url) {
            this.path = path;
            this.url  = url;
            includes.add(path);
        }

        boolean isLoaded() {
            return includes.parallelStream().allMatch(
                    path -> Files.exists(Paths.get(releasePath.concat(path)))
            );
        }

        void addInclude(String path) {}
    }

    public class IndexEntryDir extends IndexEntryFile {

        IndexEntryDir(String path, String url) {
            super(path, url);
            includes.clear();
        }

        @Override
        void addInclude(String path) {
            includes.add(path);
        }
    }
}
