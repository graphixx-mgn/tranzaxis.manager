package manager.processor;

public abstract class Factory<P extends AbstractProcessor<C>, C  extends Context> {

    protected abstract P newInstance(C context);
    public final       P create(C context) {
        return newInstance(context);
    }
}
