package manager.commands.offshoot.build;

public interface IProgressHandle extends org.radixware.kernel.common.builder.api.IProgressHandle {

    @Override
    default void switchToIndeterminate() {}

    @Override
    default void switchToDeterminate(int i) {}

    @Override
    default void start() {}

    @Override
    default void start(int i) {}

    @Override
    default void progress(int i) {}

    @Override
    default void progress(String string, int i) {
        progress(0);
    }

}
