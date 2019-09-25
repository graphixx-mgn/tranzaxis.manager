package codex.service;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.instance.Instance;
import codex.instance.RemoteHost;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;

public class RemoteServiceControl<T extends IRemoteService> extends Catalog {

    private final static String PROP_INFO = "serviceInfo";

    private Instance instance;

    public RemoteServiceControl(EntityRef<RemoteHost> owner, String title) {
        super(
                owner,
                ImageUtils.getByPath("/images/services.png"),
                title,
                null
        );
        this.instance = owner.getValue().getInstance();
        try {
            setTitle(getService().getTitle());
        } catch (RemoteException | NotBoundException e) {
            //
        }

        // Properties
        model.addDynamicProp(PROP_INFO, new AnyType(), Access.Edit, this::getServiceInfo);
    }

    public Instance getInstance() {
        return instance;
    }

    @SuppressWarnings("unchecked")
    public T getService() throws RemoteException, NotBoundException {
        return (T) getInstance().getService(getPID());
    }

    public Iconified getServiceInfo() {
        return null;
    }

    protected void serviceCallFault(Exception e) {
        MessageBox.show(
                MessageType.ERROR,
                MessageFormat.format("<b>Remote service call fault:</b>{0}", e.getMessage())
        );
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
}
