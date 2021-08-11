package manager.processor;

public abstract class Context {

    private   final Class<?> provision;
    protected Context(Class<?> provision) {
        this.provision = provision;
    }

    public final Class<?> getProvisionClass() {
        return provision;
    }

}
