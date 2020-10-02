package manager.commands.offshoot.build;

import java.io.IOException;
import java.rmi.RemoteException;

abstract class BuildingNotifier implements IBuildingNotifier {

    BuildingNotifier() throws IOException, RemoteException {}

}
