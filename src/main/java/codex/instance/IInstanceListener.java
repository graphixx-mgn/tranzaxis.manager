package codex.instance;

@FunctionalInterface
public interface IInstanceListener {
    
    public void instanceLinked(Instance instance);
    
    default public void instanceUnlinked(Instance instance) {};
    
}
