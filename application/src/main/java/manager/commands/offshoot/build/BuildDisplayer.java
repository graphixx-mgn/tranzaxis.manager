package manager.commands.offshoot.build;

import org.radixware.kernel.common.builder.api.IBuildDisplayer;
import org.radixware.kernel.common.builder.api.IDialogUtils;
import org.radixware.kernel.common.builder.api.IProgressHandle;
import org.radixware.kernel.common.builder.api.IProgressHandleFactory;
import org.radixware.kernel.common.builder.api.IStatusDisplayer;
import org.radixware.kernel.common.defs.ads.build.Cancellable;

public class BuildDisplayer implements IBuildDisplayer {
    
    private final IDialogUtils     dialogUtils = new BuildDialogUtils();
    private final IStatusDisplayer statusDisplayer = new BuildStatusDisplayer();
    private final IProgressHandle  progressHandle;
    private final IProgressHandleFactory handleFactory = new BuildHandleFactory();
    
    
    public BuildDisplayer(IProgressHandle progressHandle) {
        this.progressHandle = progressHandle;
    } 

    @Override
    public IProgressHandleFactory getProgressHandleFactory() {
        return handleFactory;
    }

    @Override
    public IStatusDisplayer getStatusDisplayer() {
        return statusDisplayer;
    }

    @Override
    public IDialogUtils getDialogUtils() {
        return dialogUtils;
    }
    
    private class BuildDialogUtils implements IDialogUtils {

        @Override
        public void messageError(String error) {}

        @Override
        public void messageInformation(String information) {}

        @Override
        public void messageError(Exception ex) {}
    }
    
    private class BuildStatusDisplayer implements IStatusDisplayer {

        @Override
        public void setStatusText(String text) {}
    }
    
    private class BuildHandleFactory implements IProgressHandleFactory {

        @Override
        public IProgressHandle createHandle(String displayName) {
            progressHandle.setDisplayName(displayName);
            return progressHandle;
        }

        @Override
        public IProgressHandle createHandle(String displayName, Cancellable allowToCancel) {
            progressHandle.setDisplayName(displayName);
            return progressHandle;
        }
    }
    
}
