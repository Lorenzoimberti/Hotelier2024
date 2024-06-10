import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteServerInterface extends Remote {
    String SERVICE_NAME = "CALLBACK_SERVER";
    String registration(String username, String password) throws RemoteException;
    String notificationService(String username, String cities) throws RemoteException;
    void registerForCallback(NotifyEventInterface ClientInterface) throws RemoteException;
    void unregisterForCallback (NotifyEventInterface Client) throws RemoteException;

}
