package manager.commands.offshoot.build;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import manager.Manager;
import manager.type.Locale;
import org.radixware.kernel.common.builder.BuildActionExecutor;
import org.radixware.kernel.common.builder.RadixObjectsProcessor;
import org.radixware.kernel.common.builder.api.IBuildEnvironment;
import org.radixware.kernel.common.check.RadixProblem;
import org.radixware.kernel.common.defs.Definition;
import org.radixware.kernel.common.defs.Module;
import org.radixware.kernel.common.defs.RadixObject;
import org.radixware.kernel.common.defs.VisitorProvider;
import org.radixware.kernel.common.defs.ads.AdsDefinition;
import org.radixware.kernel.common.defs.ads.build.Cancellable;
import org.radixware.kernel.common.defs.ads.common.AdsVisitorProviders;
import org.radixware.kernel.common.defs.ads.localization.AdsLocalizingBundleDef;
import org.radixware.kernel.common.defs.ads.module.AdsModule;
import org.radixware.kernel.common.enums.EDefType;
import org.radixware.kernel.common.enums.ERuntimeEnvironmentType;
import org.radixware.kernel.common.repository.Branch;
import org.radixware.kernel.common.repository.Layer;
import org.radixware.kernel.common.repository.ads.AdsSegment;
import org.radixware.kernel.common.resources.icons.RadixIcon;
import org.radixware.kernel.common.types.Id;
import javax.swing.*;


public class SourceBuilder {
    
    private static final EnumSet<ERuntimeEnvironmentType> TARGET_ENV = EnumSet.allOf(ERuntimeEnvironmentType.class);
    
    static int enumerateModules(Branch branch) {
        HashMap<Id, Module>     modulesIndex = new HashMap<>();
        HashMap<Id, Definition> defsIndex = new HashMap<>();
        ArrayList<Definition>   result = new ArrayList<>();

        List<Layer> layers = branch.getLayers().getInOrder();
        layers.forEach((layer) -> {
            modulesIndex.clear();
            try {
                if (layer.isReadOnly()) return;

                AdsSegment segment = (AdsSegment) layer.getAds();
                segment.getModules().list().parallelStream().filter((module) -> {
                    return !module.isUnderConstruction();
                }).forEachOrdered((module) -> {
                    if (!modulesIndex.containsKey(module.getId())) {
                        modulesIndex.put(module.getId(), module);
                    }
                });

                defsIndex.clear();

                ExecutorService executor = Executors.newCachedThreadPool();
                executor.invokeAll(modulesIndex.values().parallelStream().map((module) -> {
                    return new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            AdsModule m = (AdsModule) module;
                            while (m != null) {
                                if (!m.isReadOnly()) {
                                    if (!m.getLayer().isLocalizing()) {
                                        final AdsLocalizingBundleDef moduleBundle = ((AdsModule) module).findExistingLocalizingBundle();
                                        if (moduleBundle != null) {
                                            if (!defsIndex.containsKey(moduleBundle.getId())) {
                                                defsIndex.put(moduleBundle.getId(), moduleBundle);
                                            }
                                        }
                                        for (AdsDefinition def : m.getDefinitions()) {
                                            if (!defsIndex.containsKey(def.getId())) {
                                                defsIndex.put(def.getId(), def);
                                            }
                                            if (def.getDefinitionType() != EDefType.LOCALIZING_BUNDLE) {
                                                AdsLocalizingBundleDef bundle = def.findExistingLocalizingBundle();
                                                if (bundle != null) {
                                                    if (!defsIndex.containsKey(bundle.getId())) {
                                                        defsIndex.put(bundle.getId(), bundle);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                m = m.findOverwritten();
                            }
                            return null;
                        }
                    };
                }).collect(Collectors.toList())).stream().forEachOrdered((future) -> {
                    try {
                        future.get();
                        result.addAll(defsIndex.values());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                // Do nothing
            }
        });

        Set<Definition> checkedDefinitions = new HashSet<>();
        checkedDefinitions.addAll(result);

        final AtomicInteger totalModules = new AtomicInteger(0);
        TARGET_ENV.forEach((env) -> {
            final RadixObjectsProcessor.ICollector collector = new RadixObjectsProcessor.ICollector() {

                int count;
                Set<RadixObject> radixObjects = new HashSet<>();

                @Override
                public void accept(RadixObject radixObject) {
                    if (radixObjects.add(radixObject)) {
                        count++;
                    }
                }

                @Override
                public int getCount() {
                    return count;
                }

                @Override
                public Collection<RadixObject> get() {
                    return radixObjects;
                }
            };

            VisitorProvider visitor = AdsVisitorProviders.newCompileableDefinitionsVisitorProvider(env);
            checkedDefinitions.forEach((context) -> {
                context.visit(collector, visitor);
            });
            totalModules.addAndGet(collector.get().parallelStream().map((ro) -> {
                Definition definition = (Definition) ro;
                if (definition instanceof Module) {
                    return definition;
                } else {
                    return definition.getModule();
                }
            }).collect(Collectors.toSet()).size());
        });
        return totalModules.get();
    }
    
    public static void main(String[] args12) throws Exception {
        Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
        if (prefs.get("guiLang", null) != null) {
            Locale localeEnum = Locale.valueOf(prefs.get("guiLang", null));
            java.lang.System.setProperty("user.language", localeEnum.getLocale().getLanguage());
            java.lang.System.setProperty("user.country",  localeEnum.getLocale().getCountry());
        }
        
        Integer port  = Integer.valueOf(System.getProperty("port"));
        UUID    uuid  = UUID.fromString(System.getProperty("uuid"));
        String  path  = System.getProperty("path");
        Boolean clean = "1".equals(System.getProperty("clean"));

        Registry reg = LocateRegistry.getRegistry(port);
        IBuildingNotifier notifier = (IBuildingNotifier) reg.lookup(BuildingNotifier.class.getCanonicalName());
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                notifier.error(uuid, ex);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });

        Map<String, ImageIcon> IMG_CACHE = new HashMap<>();

        final AtomicInteger totalModules = new AtomicInteger(0);
        IBuildEnvironment env = new BuildEnvironment(
            TARGET_ENV,
            new BuildFlowLogger() {
                @Override
                public void problem(RadixProblem problem) {
                    final Definition definition = problem.getSource().getDefinition();
                    final RadixIcon  radixIcon  = definition != null ? definition.getIcon() : problem.getSource().getIcon();
                    final String defId   = definition != null ? definition.getId().toString() : problem.getSource().getQualifiedName();
                    final String defName = problem.getSource().getQualifiedName();
                    final String imgUri  = definition != null ? radixIcon.getResourceUri() : radixIcon.getResourceUri();
                    final String message = problem.getMessage();

                    if (!IMG_CACHE.containsKey(imgUri)) {
                        try {
                            IMG_CACHE.put(
                                    imgUri,
                                    new ImageIcon(SvgImageLoader.loadSvg(
                                            ClassLoader.getSystemClassLoader().getResource(imgUri),
                                            radixIcon.getIcon().getIconWidth()
                                    ))
                            );
                        } catch (IOException e) {
                            //
                        }
                    }
                    final ImageIcon icon = IMG_CACHE.get(imgUri);
                    try {
                        notifier.event(uuid, problem.getSeverity(), defId, defName, icon, message);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                }
                @Override
                public Cancellable getCancellable() {
                    return new Cancellable() {
                        @Override
                        public boolean cancel() {
                            return false;
                        }

                        @Override
                        public boolean wasCancelled() {
                            try {
                                notifier.isPaused(uuid);
                            } catch (RemoteException e) {
                                throw new RuntimeException(e.getMessage());
                            }
                            return false;
                        }
                    };
                }
            },
            new IProgressHandle() {
                private final Pattern MODULE_BUILD = Pattern.compile("^Build module: (.*): $");
                private final Set<String> builtModules = new HashSet<>();

                @Override
                public void setDisplayName(String name) {
                    Matcher matcher = MODULE_BUILD.matcher(name);
                    try {
                        if (matcher.find() && !builtModules.contains(matcher.group(1))) {
                            builtModules.add(matcher.group(1));
                            int progress = 100*builtModules.size()/totalModules.get();
                            notifier.description(uuid, matcher.group(1));
                            notifier.progress(uuid, progress);
                        } else {
                            notifier.description(uuid, name);
                        }
                    } catch (RemoteException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                }

                @Override
                public void finish() {}
            }
        ) {
            @Override
            public Logger getLogger() {
                return new Logger(BuildEnvironment.class.getName(), null) {
                    @Override
                    public void log(Level level, String msg, Throwable thrown) {
                        try {
                            notifier.error(uuid, thrown);
                        } catch (RemoteException e) {
                            throw new RuntimeException(e.getMessage());
                        }
                    }
                };
            }

            @Override
            public BuildActionExecutor.EBuildActionType getActionType() {
                return clean ? BuildActionExecutor.EBuildActionType.CLEAN_AND_BUILD : BuildActionExecutor.EBuildActionType.BUILD;
            }
        };

        Branch branch = Branch.Factory.loadFromDir(new File(path));
        env.getBuildDisplayer().getProgressHandleFactory().createHandle("Load definitions...");
        totalModules.set(enumerateModules(branch));
        BuildActionExecutor executor = new BuildActionExecutor(env);
        executor.execute(branch);
    }
    
}
