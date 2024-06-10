import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class ServerMain {
    private static final int DEFAULT_RANKING_UPDATE_TIME = 30; //30 secondi
    private static String serverConfigFile = "ServerConfig.json"; //in locale src/
    private static ServerConfig serverConfig = new ServerConfig(); //Oggetto per i parametri di configurazione

    public static void main(String[] args) throws RemoteException {
        try {
            serverConfig = Utils.getServerConfig(serverConfigFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HOTELIERServer server = new HOTELIERServer();

        //Creazione e avvio del thread per il server RMI
        Thread rmiThread = new Thread(() -> {
            try {
                startRMIServer(server);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
        rmiThread.start();

        //Avvio del server
        if (args.length > 0 && args[0] != null){
            server.start(Integer.parseInt(args[0].trim()));
        }
        else server.start(DEFAULT_RANKING_UPDATE_TIME);
    }

    //Metodo che avvia thread per RMI
    private static void startRMIServer(HOTELIERServer server) throws RemoteException {
        //Creazione dello stub e del registro
        //Esporto dinamicamente l'oggetto su una porta anonima
        RemoteServerInterface stub = (RemoteServerInterface) UnicastRemoteObject.exportObject(server, 0);
        LocateRegistry.createRegistry(serverConfig.getRmiPort());
        // Registrazione dello stub
        Registry r = LocateRegistry.getRegistry(serverConfig.getRmiPort());
        try {
            r.rebind(stub.SERVICE_NAME, stub);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        System.out.println("Server RMI avviato su porta " + serverConfig.getRmiPort());
    }
}
