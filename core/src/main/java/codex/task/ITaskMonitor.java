package codex.task;

interface ITaskMonitor extends ITaskListener {

    void registerTask(ITask task);
    void unregisterTask(ITask task);
    void clearRegistry();

    default void setTaskRecipient(ITaskMonitor monitor) {}

    class MonitorAdapter implements ITaskMonitor {

        @Override
        public void registerTask(ITask task) {}

        @Override
        public void unregisterTask(ITask task) {}

        @Override
        public void clearRegistry() {}
    }
}
