
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import manager.Manager;


public class Loader {
    private static Manager manager = null;
    
    public static void main(String[] args) {
        String uniqueAppId = Manager.class.getCanonicalName();
        try {
            JUnique.acquireLock(uniqueAppId, (message) -> {
                manager.show();
                return null;
            });
        } catch (AlreadyLockedException e) {
            JUnique.sendMessage(uniqueAppId, "OPEN");
            return;
        }
        manager = new Manager();
    }
    
}
