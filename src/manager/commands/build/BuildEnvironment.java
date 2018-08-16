package manager.commands.build;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import org.radixware.kernel.common.builder.BuildActionExecutor;
import org.radixware.kernel.common.builder.api.IBuildDisplayer;
import org.radixware.kernel.common.builder.api.IBuildEnvironment;
import org.radixware.kernel.common.builder.api.IBuildProblemHandler;
import org.radixware.kernel.common.builder.api.ILifecycleManager;
import org.radixware.kernel.common.builder.api.IMutex;
import org.radixware.kernel.common.builder.api.IProgressHandle;
import org.radixware.kernel.common.builder.check.common.CheckOptions;
import org.radixware.kernel.common.check.RadixProblem;
import org.radixware.kernel.common.defs.Definition;
import org.radixware.kernel.common.defs.ads.build.BuildOptions;
import org.radixware.kernel.common.defs.ads.build.IFlowLogger;
import org.radixware.kernel.common.enums.ERuntimeEnvironmentType;

public class BuildEnvironment implements IBuildEnvironment {
    
    private long startTime;
    
    private final CheckOptions checkOptions;
    private final BuildOptions buildOptions;
    private final IFlowLogger  flowLogger;
    
    private final IBuildProblemHandler problemHandler;
    private final IBuildDisplayer      buildDisplayer;
    private final ILifecycleManager    lfcManager = new LifecycleManager();
    private final IMutex               mutex = new Mutex();

    public BuildEnvironment(EnumSet<ERuntimeEnvironmentType> env, IFlowLogger  flowLogger, IProgressHandle progressHandle) {
        checkOptions = new CheckOptions();
        checkOptions.setCheckAllOvrPathes(true);
        checkOptions.setDbConnection(null);
        checkOptions.setCheckSqlClassQuerySyntax(true);
        checkOptions.setMaxSqlQueryVariants(16);
        checkOptions.setCheckDocumentation(false);
        
        buildOptions = BuildOptions.Factory.newInstance();
        buildOptions.setEnvironment(env);
        buildOptions.setMultythread(true);
        buildOptions.setVerifyClassLinkage(false);
        
        this.flowLogger = flowLogger;
        this.problemHandler = new ProblemHandler(flowLogger);
        buildDisplayer =  new BuildDisplayer(progressHandle);
    }
    
    
    @Override
    public void prepare() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger(BuildEnvironment.class.getName());
    }

    @Override
    public BuildActionExecutor.EBuildActionType getActionType() {
        return BuildActionExecutor.EBuildActionType.BUILD;
    }

    @Override
    public IFlowLogger getFlowLogger() {
        return flowLogger;
    }

    @Override
    public IBuildProblemHandler getBuildProblemHandler() {
        return problemHandler;
    }

    @Override
    public BuildOptions getBuildOptions() {
        return buildOptions;
    }

    @Override
    public CheckOptions getCheckOptions() {
        return checkOptions;
    }

    @Override
    public IBuildDisplayer getBuildDisplayer() {
        return buildDisplayer;
    }

    @Override
    public IMutex getMutex() {
        return mutex;
    }

    @Override
    public ILifecycleManager getLifecycleManager() {
        return lfcManager;
    }

    @Override
    public void displayResults() {}

    @Override
    public void targetsDetermined(Set<Definition> set, List<Definition> list) {}

    @Override
    public void complete() {}
    
    
    public class ProblemHandler implements IBuildProblemHandler {

        private int errorsCount = 0;
        private int warningsCount = 0;
        private final IFlowLogger flowLogger;
        private final List<RadixProblem> errors = new LinkedList<>();

        public ProblemHandler(IFlowLogger flowLogger) {
            this.flowLogger = flowLogger;
        }

        @Override
        public void accept(RadixProblem problem) {
            if (problem.getSeverity().equals(RadixProblem.ESeverity.ERROR)) {
                errorsCount++;
                errors.add(problem);
            } else if (problem.getSeverity() == RadixProblem.ESeverity.WARNING) {
                warningsCount++;
            }
            flowLogger.problem(problem);
        }

        @Override
        public void clear() {
            errors.clear();
            errorsCount = 0;
            warningsCount = 0;
        }

        @Override
        public boolean wasErrors() {
            return errorsCount > 0;
        }
        
        public List<RadixProblem> getErrors() {
            return new LinkedList<>(errors);
        }

        public int getErrorsCount() {
            return errorsCount;
        }

        public int getWarningsCount() {
            return warningsCount;
        }
    }
    
    private class LifecycleManager implements ILifecycleManager {

        @Override
        public void saveAll() {}

        @Override
        public void exit() {
            System.exit(0);
        }
    }
    
    private class Mutex implements IMutex {

        Lock l = new ReentrantLock();

        @Override
        public void readAccess(Runnable r) {
            r.run();
        }

        @Override
        public Lock getLongProcessLock() {
            return l;
        }
    }
    
}
