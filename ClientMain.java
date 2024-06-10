import java.rmi.RemoteException;

public class ClientMain {
    public static void main(String[] args) throws RemoteException {
        HOTELIERClient client = new HOTELIERClient();
        client.start();
    }
}
