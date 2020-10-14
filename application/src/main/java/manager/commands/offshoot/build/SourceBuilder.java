package manager.commands.offshoot.build;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.radixware.kernel.common.builder.BuildActionExecutor;
import org.radixware.kernel.common.builder.RadixObjectsProcessor;
import org.radixware.kernel.common.builder.api.IBuildEnvironment;
import org.radixware.kernel.common.check.RadixProblem;
import org.radixware.kernel.common.defs.Definition;
import org.radixware.kernel.common.defs.Module;
import org.radixware.kernel.common.defs.RadixObject;
import org.radixware.kernel.common.defs.VisitorProvider;
import org.radixware.kernel.common.defs.ads.build.Cancellable;
import org.radixware.kernel.common.defs.ads.common.AdsVisitorProviders;
import org.radixware.kernel.common.enums.ERuntimeEnvironmentType;
import org.radixware.kernel.common.repository.Branch;
import org.radixware.kernel.common.resources.icons.RadixIcon;
import javax.swing.*;


public class SourceBuilder {
    
    private static final EnumSet<ERuntimeEnvironmentType> TARGET_ENV = EnumSet.allOf(ERuntimeEnvironmentType.class);

    static int enumerateModules(IBuildEnvironment env, Branch branch) throws Exception {
        Class contextCheckerClass = Class.forName("org.radixware.kernel.common.builder.ContextChecker");
        Class contextInfoClass    = Class.forName("org.radixware.kernel.common.builder.ContextChecker$ContextInfo");

        Constructor constructor = contextCheckerClass.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        Object checker = constructor.newInstance(true);

        Method determineTargets = contextCheckerClass.getDeclaredMethod(
                "determineTargets",
                BuildActionExecutor.EBuildActionType.class,
                RadixObject[].class,
                IBuildEnvironment.class,
                boolean.class
        );
        determineTargets.setAccessible(true);
        Object contextInfo = determineTargets.invoke(
                checker,
                BuildActionExecutor.EBuildActionType.BUILD,
                new RadixObject[] {branch},
                env,
                false
        );

        Field targets = contextInfoClass.getDeclaredField("targets");
        targets.setAccessible(true);
        Set<Definition> checkedDefinitions = (Set<Definition>) targets.get(contextInfo);

        final AtomicInteger modules = new AtomicInteger(0);
        TARGET_ENV.forEach((environment) -> {
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

            VisitorProvider visitor = AdsVisitorProviders.newCompileableDefinitionsVisitorProvider(environment);
            checkedDefinitions.forEach((context) -> context.visit(collector, visitor));
            modules.addAndGet(collector.get().parallelStream().map((ro) -> {
                Definition definition = (Definition) ro;
                if (definition instanceof Module) {
                    return definition;
                } else {
                    return definition.getModule();
                }
            }).collect(Collectors.toSet()).size());
        });
        return modules.get();
    }
    
    public static void main(String[] args12) throws Exception {
        final Integer port  = Integer.valueOf(System.getProperty("port"));
        final String  path  = System.getProperty("path");
        final Boolean clean = "1".equals(System.getProperty("clean"));

        final Registry reg = LocateRegistry.getRegistry(port);
        final IBuildingNotifier notifier = (IBuildingNotifier) reg.lookup(BuildingNotifier.class.getTypeName());

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                notifier.error(ex);
            } catch (RemoteException ignore) {}
        });

        Map<String, ImageIcon> IMG_CACHE = new HashMap<>();

        final AtomicInteger totalModules = new AtomicInteger(0);
        final IBuildEnvironment env = new BuildEnvironment(
            TARGET_ENV,
            new BuildFlowLogger() {

                @Override
                public void problem(RadixProblem problem) {
                    final Definition definition = problem.getSource().getDefinition();
                    final RadixIcon  radixIcon  = definition != null ? definition.getIcon() : problem.getSource().getIcon();
                    final String     defId = definition != null ? definition.getId().toString() : problem.getSource().getQualifiedName();
                    final String     defName = problem.getSource().getQualifiedName();
                    final String     imgUri  = definition != null ? radixIcon.getResourceUri() : radixIcon.getResourceUri();
                    final String     message = problem.getMessage();

                    if (!IMG_CACHE.containsKey(imgUri)) {
                        try {
                            IMG_CACHE.put(
                                    imgUri,
                                    new ImageIcon(SvgImageLoader.loadSvg(
                                            ClassLoader.getSystemClassLoader().getResource(imgUri),
                                            radixIcon.getIcon().getIconWidth()
                                    ))
                            );
                        } catch (IOException ignore) {}
                    }
                    final ImageIcon icon = IMG_CACHE.get(imgUri);
                    try {
                        notifier.event(problem.getSeverity(), defId, defName, icon, message);
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
                                notifier.isPaused();
                            } catch (RemoteException ignore) {}
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
                            int progress = Math.min(100 * builtModules.size() / totalModules.get(), 100);
                            notifier.progress(progress);
                        }
                        notifier.description(name);
                    } catch (RemoteException e) {
                        e.printStackTrace();
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
                            notifier.error(thrown);
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
        totalModules.set(enumerateModules(env, branch));

        BuildActionExecutor executor = new BuildActionExecutor(env);
        executor.execute(branch);

        System.exit(0);
    }
    
}
