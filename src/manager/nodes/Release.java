package manager.nodes;

import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.CancelException;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import manager.svn.SVN;
import manager.xml.DirectoryDocument;
import manager.xml.ReleaseDocument;
import org.apache.xmlbeans.XmlException;
import org.bridj.util.Pair;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;


public class Release extends BinarySource {
    
    private final static ExecutorService   EXECUTOR = Executors.newFixedThreadPool(10);
    private final static Predicate<String> XML_PATTERN = Pattern.compile("^[^/]*.xml").asPredicate();
    private final static Pattern INCLUDE = Pattern.compile("Include FileName=\"(.*)\"");
    private final static Pattern FILE    = Pattern.compile("File Name=\"(.*)\"\\s");
    private final static Pattern LAYER   = Pattern.compile("\\sBaseLayerURIs=\"([\\w\\.]*)\"\\s");

    public Release(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/release.png"), title);
        
        // Properties
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getPID();
        });
    }

    @Override
    public Class getChildClass() {
        return null;
    }
    
    @Override
    public String getRemotePath() {
        return new StringJoiner("/")
            .add((String) this.model.getOwner().model.getValue("repoUrl"))
            .add("releases")
            .add(model.getPID()).toString();
    }
    
    @Override
    public final String getLocalPath() {
        IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
        String workDir = EAS.getRoot().model.getValue("workDir").toString();
        String repoUrl = (String) this.model.getOwner().model.getValue("repoUrl");
        
        StringJoiner wcPath = new StringJoiner(File.separator)
            .add(workDir)
            .add("releases")
            .add(Repository.urlToDirName(repoUrl))
            .add(model.getPID());
        return wcPath.toString();
    }
    
    public Map<String, Path> getRequiredLayers(String topLayer, boolean online) {
        if (online) {
            try {
                ISVNAuthenticationManager authMgr = ((Repository) model.getOwner()).getAuthManager();
                InputStream in = SVN.readFile(getRemotePath(), "release.xml", authMgr);
                ReleaseDocument releaseDoc = ReleaseDocument.Factory.parse(in);
                manager.xml.Release.Branch.Layer[] layers = releaseDoc.getRelease().getBranch().getLayerArray();
                return createLayerChain(layers, topLayer);
            } catch (SVNException | XmlException | IOException e) {
                return new LinkedHashMap<String, Path>(){{
                    put(topLayer, null);
                }};
            }
        } else {
            return createLayerChain(topLayer);
        }
    }
    
    private Map<String, Path> createLayerChain(String nextLayer) {
        Map<String, Path> chain = new LinkedHashMap<>();
        Path layerDef = Paths.get(getLocalPath()+File.separator+nextLayer+File.separator+"layer.xml");
        if (Files.exists(layerDef)) {
            try {
                chain.put(nextLayer, layerDef);
                String content = new String(Files.readAllBytes(layerDef), Charset.forName("UTF-8"));
                Matcher layerMatcher = LAYER.matcher(content);
                if (layerMatcher.find()) {
                    chain.putAll(createLayerChain(layerMatcher.group(1)));
                }
            } catch (IOException e) {}
        } else {
            chain.put(nextLayer, null);
        }
        return chain;
    }
    
    private Map<String, Path> createLayerChain(manager.xml.Release.Branch.Layer[] layers, String nextLayer) {
        Map<String, Path> chain = new LinkedHashMap<>();
        for (manager.xml.Release.Branch.Layer layer : layers) {
            if (layer.getUri().equals(nextLayer)) {
                Path layerDef = Paths.get(getLocalPath()+File.separator+nextLayer+File.separator+"layer.xml");
                chain.put(layer.getUri(), layerDef);
                if (!layer.getBaseLayerURIs().isEmpty()) {
                    chain.putAll(createLayerChain(layers, (String) layer.getBaseLayerURIs().get(0)));
                }
            }
        }
        return chain;
    }
    
    private Map<String, String> getLayerPaths(List<String> layers) throws SVNException {
        Map<String, String> map = new LinkedHashMap<>();
        ISVNAuthenticationManager authMgr = ((Repository) model.getOwner()).getAuthManager();
        List<SVNDirEntry> entries = SVN.list(getRemotePath(), authMgr);
        entries.stream()
            .filter((dirEntry) -> (dirEntry.getKind() == SVNNodeKind.DIR && layers.contains(dirEntry.getName())))
            .forEachOrdered((dirEntry) -> {
                map.put(
                    dirEntry.getName(),
                    getRemotePath()+"/"+dirEntry.getName()
                );
            });
        return map;
    }
    
    public static boolean checkStructure(String localPath) {
        Path path = Paths.get(localPath);
        if (!Files.exists(path)) {
            return false;
        }
        if (!XML_PATTERN.test(path.getFileName().toString())) {
            return true;
        }
        try {
            String content = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));

            Matcher includeMatcher = INCLUDE.matcher(content);
            final Stream.Builder<String> includes = Stream.builder();
            while (includeMatcher.find()) includes.add(includeMatcher.group(1));

            Matcher fileMatcher = FILE.matcher(content);
            final Stream.Builder<String> files = Stream.builder();
            while (fileMatcher.find()) files.add(fileMatcher.group(1));

            return Stream
                    .concat(
                        includes.build(), 
                        files.build().filter((fileName) -> {
                                return 
                                    fileName.startsWith("bin/") || 
                                    fileName.startsWith("lib/") ||
                                    XML_PATTERN.test(fileName);
                            })
                    ).parallel()
                    .allMatch((include) -> {
                        return checkStructure(path.getParent()+File.separator+include);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private static void loadIndex(int level, ExecutorService executor, Map<String, String> mapLocalToRemote, ISVNAuthenticationManager authMgr) {
        List<Callable<Map.Entry<String, String>>> callables = mapLocalToRemote.entrySet().parallelStream()
                .map((pathEntry) -> {
                    return new Callable<Map.Entry<String, String>>() {
                        @Override
                        public Map.Entry<String, String> call() throws Exception {
                            if (!Files.exists(Paths.get(pathEntry.getKey()).getParent())) {
                                SVN.export(
                                        pathEntry.getValue().substring(0, pathEntry.getValue().lastIndexOf("/")),
                                        Paths.get(pathEntry.getKey()).getParent().toString(), 
                                        authMgr,
                                        SVNDepth.FILES
                                );
                            }
                            if (!Files.exists(Paths.get(pathEntry.getKey()))) {
                                SVN.export(
                                        pathEntry.getValue(),
                                        Paths.get(pathEntry.getKey()).toString(), 
                                        authMgr, 
                                        SVNDepth.EMPTY
                                );
                            }
                            return new Pair<>(pathEntry.getKey(), pathEntry.getValue());
                        }
                    };
                }).collect(Collectors.toList());

        try {
            executor
                .invokeAll(callables)
                .stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).forEach((loadedEntry) -> {
                    File dirFile = new File(loadedEntry.getKey());
                    try {
                        DirectoryDocument dirXml = DirectoryDocument.Factory.parse(dirFile);
                        // Load includes
                        loadIndex(level +1, executor, 
                                (dirXml.getDirectory().getIncludes() == null ? 
                                    Stream.empty() : 
                                    Stream.of(dirXml.getDirectory().getIncludes().getIncludeArray()).parallel()
                                    .map((include) -> {
                                        return include.getFileName();
                                    })
                                ).collect(Collectors.toMap(
                                    fileName -> dirFile.getParentFile().getPath()+File.separator+fileName, 
                                    fileName -> loadedEntry.getValue().substring(0, loadedEntry.getValue().lastIndexOf("/"))+"/"+fileName 
                                )),
                                authMgr
                        );

                        // Load files
                        loadIndex(level +1, executor, 
                                (dirXml.getDirectory().getFileGroups() == null ? 
                                    Stream.empty() : 
                                    Stream.of(dirXml.getDirectory().getFileGroups().getFileGroupArray())
                                        .map((fileGroup) -> {
                                            return Stream.of(fileGroup.getFileArray())
                                                    .filter((file) -> {
                                                        return file.getName().matches("[^/]*.xml") && 
                                                               !Files.exists(Paths.get(dirFile.getParentFile().getPath()+File.separator+file.getName()));
                                                    })
                                                    .map((file) -> {
                                                        return file.getName();
                                                    });
                                        }).flatMap(x-> x)
                                        ).collect(Collectors.toMap(
                                            fileName ->  dirFile.getParentFile().getPath()+File.separator+fileName,
                                            fileName -> loadedEntry.getValue().substring(0, loadedEntry.getValue().lastIndexOf("/"))+"/"+fileName 
                                        )),
                                authMgr
                        );
                    } catch (IOException e) {
                        throw new Error(e.getMessage());
                    } catch (XmlException e) {}
                });
        } catch (InterruptedException e) {
            throw new CancelException();
        }
    }
    
    private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
    
    private static Map<String, String> findStructureGaps(String localPath, String remotePath) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            File dirFile = new File(localPath);
            DirectoryDocument dirXml = DirectoryDocument.Factory.parse(dirFile);

            Stream<String> includes = dirXml.getDirectory().getIncludes() == null ? 
                Stream.empty() : 
                Stream.of(dirXml.getDirectory().getIncludes().getIncludeArray()).parallel().filter((include) -> {
                    return include.getFileName().endsWith("/directory.xml");
                }).map((include) -> {
                    return include.getFileName();
                });

            Map<String, String> m = includes
                .collect(Collectors.toMap(
                        fileName -> dirFile.getParentFile().getPath()+File.separator+fileName, 
                        fileName -> remotePath.substring(0, remotePath.lastIndexOf("/"))+"/"+fileName
                ));
            m.forEach((local, remote) -> {
                map.putAll(findStructureGaps(local, remote));
            });

            Stream<String> binaries = dirXml.getDirectory().getFileGroups() == null ?
                Stream.empty() : 
                Stream.of(dirXml.getDirectory().getFileGroups().getFileGroupArray())
                        .map((fileGroup) -> {
                            return Stream.of(fileGroup.getFileArray())
                                    .filter((file) -> {
                                        return (
                                                    file.getName().startsWith("bin/") || 
                                                    file.getName().startsWith("lib/")
                                                ) && 
                                                !Files.exists(Paths.get(dirFile.getParentFile().getPath()+File.separator+file.getName()));
                                    })
                        .map((file) -> {
                            return file.getName().replaceAll("(.*)/.*", "$1");
                        });
                }).flatMap(x-> x).filter(distinctByKey(fileName -> fileName));

            map.putAll(binaries.collect(Collectors.toMap(
                    fileName -> dirFile.getParentFile().getPath()+File.separator+fileName,
                    fileName -> remotePath.substring(0, remotePath.lastIndexOf("/"))+"/"+fileName
            )));
        } catch (XmlException e) {
        } catch (IOException e) {
            throw new Error(e.getMessage());
        }
        return map;
    }

    public class LoadCache extends AbstractTask<Void> {
        
        private final List<String> requiredLayers;
        
        public LoadCache(List<String> requiredLayers) {
            super(MessageFormat.format(
                    Language.get(Release.class.getSimpleName(), "cache@task"),
                    Release.this.getLocalPath()
            ));
            this.requiredLayers = requiredLayers;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(Release.class.getSimpleName(), "cache@task.index"));
            Map<String, String> layerPaths = getLayerPaths(requiredLayers);
            loadIndex(1, EXECUTOR,
                layerPaths.entrySet().parallelStream()
                    .collect(Collectors.toMap(
                        entry -> getLocalPath()+File.separator+entry.getKey()+File.separator+"directory.xml",
                        entry -> entry.getValue()+"/directory.xml"
                    )),
                ((Repository) model.getOwner()).getAuthManager()
            );
            setProgress(0, Language.get(Release.class.getSimpleName(), "cache@task.bins"));
            Map<String, String> absentFiles = new LinkedHashMap<>();
            layerPaths.entrySet().stream().forEach((entry) -> {
                absentFiles.putAll(findStructureGaps(
                        getLocalPath()+File.separator+entry.getKey()+File.separator+"directory.xml",
                        entry.getValue()+"/directory.xml"
                ));
            });
            loadGaps(EXECUTOR, absentFiles);
            return null;
        }
        
        private void loadGaps(ExecutorService executor, Map<String, String> mapLocalToRemote) {
            AtomicInteger current = new AtomicInteger(0);
            String releaseBase = Release.this.getRemotePath();
            List<Callable<Map.Entry<String, String>>> callables = mapLocalToRemote.entrySet().parallelStream()
                    .map((pathEntry) -> {
                        return new Callable<Map.Entry<String, String>>() {
                            @Override
                            public Map.Entry<String, String> call() throws Exception {
                                SVN.export(pathEntry.getValue(), pathEntry.getKey(), null, null);
                                current.addAndGet(1);
                                setProgress(
                                        current.get() * 100 / mapLocalToRemote.size(), 
                                        MessageFormat.format(
                                                Language.get(Release.class.getSimpleName(), "cache@task.progress"),
                                                pathEntry.getValue().replace(releaseBase, "")
                                        )
                                );
                                return new Pair<>(pathEntry.getKey(), pathEntry.getValue());
                            }
                        };
                    }).collect(Collectors.toList());

            try {
                executor.invokeAll(callables);
            } catch (InterruptedException e) {
                throw new CancelException();
            }
        }

        @Override
        public void finished(Void t) {}
    
    }
    
}
