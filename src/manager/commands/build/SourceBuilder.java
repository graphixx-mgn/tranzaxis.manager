package manager.commands.build;

import java.io.File;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import manager.Manager;
import manager.commands.BuildWC;
import manager.type.Locale;
import org.radixware.kernel.common.builder.BuildActionExecutor;
import org.radixware.kernel.common.builder.RadixObjectsProcessor;
import org.radixware.kernel.common.builder.api.IBuildEnvironment;
import org.radixware.kernel.common.builder.api.IProgressHandle;
import org.radixware.kernel.common.defs.Definition;
import org.radixware.kernel.common.defs.Module;
import org.radixware.kernel.common.defs.RadixObject;
import org.radixware.kernel.common.defs.VisitorProvider;
import org.radixware.kernel.common.defs.ads.AdsDefinition;
import org.radixware.kernel.common.defs.ads.common.AdsVisitorProviders;
import org.radixware.kernel.common.defs.ads.localization.AdsLocalizingBundleDef;
import org.radixware.kernel.common.defs.ads.module.AdsModule;
import org.radixware.kernel.common.enums.EDefType;
import org.radixware.kernel.common.enums.ERuntimeEnvironmentType;
import org.radixware.kernel.common.repository.Branch;
import org.radixware.kernel.common.repository.Layer;
import org.radixware.kernel.common.repository.ads.AdsSegment;
import org.radixware.kernel.common.types.Id;


public class SourceBuilder {
    
    private static final EnumSet<ERuntimeEnvironmentType> TARGET_ENV = EnumSet.allOf(ERuntimeEnvironmentType.class);
    
    static int  enumerateModules(Branch branch) {
        HashMap<Id, Module>     modulesIndex = new HashMap<>();
        HashMap<Id, Definition> defsIndex = new HashMap<>();
        ArrayList<Definition>   result = new ArrayList<>();
        
        try {
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
                                try {
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
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                                return null;
                            }
                        };
                    }).collect(Collectors.toList())).stream().forEachOrdered((future) -> {
                        try {
                            future.get();
                            result.addAll(defsIndex.values());
                        } catch (ExecutionException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            
            Set<Definition> checkedDefinitions = new HashSet<>();
            checkedDefinitions.addAll(result);

            final AtomicInteger totalModules = new AtomicInteger(0);
            TARGET_ENV.forEach((env) -> {
                final RadixObjectsProcessor.ICollector collector = new RadixObjectsProcessor.ICollector() {

                    public int count;
                    public Set<RadixObject> radixObjects = new HashSet<>();

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
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    public static void main(String[] args) {
        Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
        if (prefs.get("guiLang", null) != null) {
            Locale localeEnum = Locale.valueOf(prefs.get("guiLang", null));
            java.lang.System.setProperty("user.language", localeEnum.getLocale().getLanguage());
            java.lang.System.setProperty("user.country",  localeEnum.getLocale().getCountry());
        }
        
        try {
            Registry reg = LocateRegistry.getRegistry(BuildWC.RMI_PORT);
            IBuildingNotifier notifier = (IBuildingNotifier) reg.lookup(BuildingNotifier.class.getCanonicalName());
            
            AtomicInteger totalModules = new AtomicInteger(0);
            IBuildEnvironment env = new BuildEnvironment(
                TARGET_ENV, 
                new BuildFlowLogger(),
                new IProgressHandle() {

                    Pattern MODULE_BUILD = Pattern.compile("^Build module: (.*): $");
                    private Set<String> builtModules = new HashSet<>();

                    @Override
                    public void switchToIndeterminate() {}

                    @Override
                    public void switchToDeterminate(int i) {}

                    @Override
                    public void start() {}

                    @Override
                    public void start(int i) {}

                    @Override
                    public void progress(String string, int i) {
                        progress(0);
                    }

                    @Override
                    public void progress(int i) {
                        try {
                            notifier.checkPaused(args[0]);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void setDisplayName(String name) {
                        Matcher matcher = MODULE_BUILD.matcher(name);
                        try {
                            notifier.checkPaused(args[0]);
                            if (matcher.find() && !builtModules.contains(matcher.group(1))) {
                                builtModules.add(matcher.group(1));
                                int progress = 100*builtModules.size()/totalModules.get();
                                notifier.setStatus(args[0], matcher.group(1));
                                notifier.setProgress(args[0], progress);
                            } else {
                                notifier.setStatus(args[0], name);
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void finish() {}
                }
            ) {
                @Override
                public BuildActionExecutor.EBuildActionType getActionType() {
                    return args[2].equals("1") ? BuildActionExecutor.EBuildActionType.CLEAN_AND_BUILD : BuildActionExecutor.EBuildActionType.BUILD;
                }
            };
            
            try {
                Branch branch = Branch.Factory.loadFromDir(new File(args[1]));
                env.getBuildDisplayer().getProgressHandleFactory().createHandle("Load definitions...");
                totalModules.set(enumerateModules(branch));

                BuildActionExecutor executor = new BuildActionExecutor(env);
                executor.execute(branch);
            } catch (IOException e) {
                notifier.failed(args[0], e);
            }
            
            if (env.getBuildProblemHandler().wasErrors()) {
                BuildEnvironment.ProblemHandler problemHandler = (BuildEnvironment.ProblemHandler) env.getBuildProblemHandler();

                int totalErrors = problemHandler.getErrorsCount();
                int totalWarnings = problemHandler.getWarningsCount();

                Map<String, List<String>> errorIndex = new HashMap<>();

                problemHandler.getErrors().forEach((problem) -> {
                    String source = problem.getSource().getQualifiedName();
                    if (!errorIndex.containsKey(source)) {
                        errorIndex.put(source, new LinkedList<>());
                    }
                    errorIndex.get(source).add(problem.getMessage());
                });
                String message = "Build failed. Total errors: " + totalErrors + ", total warnings: " + totalWarnings;
                for (String key : errorIndex.keySet()) {
                    message = message
                            .concat("\n[")
                            .concat(key)
                            .concat("]\n - ")
                            .concat(errorIndex.get(key).stream().collect(Collectors.joining("\n - ")));
                }
                notifier.failed(args[0], new Exception(message));
            }
            notifier.finished(args[0]);
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }
    
}
