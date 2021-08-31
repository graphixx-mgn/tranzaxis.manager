package manager.processor;

public abstract class AbstractProcessor<C extends Context>  {

    private final C context;
    protected AbstractProcessor(C context) {
        this.context = context;
    }

    protected final C getContext() {
        return context;
    }
}
