import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class HOTELIERClient extends RemoteObject implements NotifyEventInterface{

    private Map<String, String> LocalRankings = Collections.synchronizedMap(new HashMap<>()); //hashmap per i ranking locali
    private static ClientConfig clientConfig = new ClientConfig();
    private static String clientConfigFile = "ClientConfig.json"; //in locale src/

    //crea nuovo call back client
    public HOTELIERClient() throws RemoteException {
        super();
    }

    public void start() {

        //Recupero delle configurazioni
        try {
            clientConfig = Utils.getClientConfig(clientConfigFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //Avvia un nuovo thread per la ricezione dei messaggi multicast
        Thread multicastThread = new Thread(() -> {
            try {
                while (true) {
                    receiveUDPMessage(); //Ricevi i messaggi multicast
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        multicastThread.start(); //Avvia il thread

        try (SocketChannel socketChannel = SocketChannel.open()) {
            Registry r = LocateRegistry.getRegistry(clientConfig.getRmiPort());
            RemoteServerInterface remoteServer = (RemoteServerInterface) r.lookup(RemoteServerInterface.SERVICE_NAME);

            //esportazione dello stub del client per callback RMI su porta anonima(0)
            NotifyEventInterface callBackObj = (NotifyEventInterface) this;
            NotifyEventInterface stubCallBack = (NotifyEventInterface) UnicastRemoteObject.exportObject(callBackObj, 0);
            //registrazione per callback
            remoteServer.registerForCallback(stubCallBack);

            socketChannel.connect(new InetSocketAddress(clientConfig.getServerIP(), clientConfig.getTcpPort()));//Connessione al server

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));//Lettore dei comandi del client

            printMenu();

            String choice = "";
            String username = "";
            String notificationServiceChoice = "";
            String favouriteCities = "";
            String result = "";

            while (!Objects.equals(choice, "esci")) {

                String command = console.readLine().trim();
                String[] arguments = command.split(",");
                //Rimuovo gli spazi all'interno della stringa
                for (int i = 0; i < arguments.length; i++) {
                    arguments[i] = arguments[i].trim();
                }
                //Formatto anche il comando iniziale togliendo gli spazi
                StringBuilder resultBuilder = new StringBuilder();
                for (int i = 0; i < arguments.length - 1; i++) {
                    resultBuilder.append(arguments[i]);
                    resultBuilder.append(",");
                }
                command = resultBuilder + arguments[arguments.length - 1];

                switch (arguments[0]) {
                    case "registrazione":
                        System.out.println("A seguito della registrazione, verra' effettuato automaticamente il login\n");
                        result = standardCommand(command, socketChannel);
                        System.out.println(result);
                        //login necesario dopo la registrazione
                        String loginResult = standardCommand("login" + "," + arguments[1] + "," + arguments[2], socketChannel);
                        while (loginResult.equals(ErrorCodes.WRONG_PSWD)) {
                            System.out.println(ErrorCodes.WRONG_PSWD + " ,ritenta il login riscrivendo il comando completo (login, ..., ...\n");
                            command = console.readLine().trim();
                            arguments = command.split(",");
                            loginResult = standardCommand("login" + "," + arguments[1] + "," + arguments[2], socketChannel);
                        }
                        System.out.println(loginResult);
                        username = arguments[1];
                        System.out.println("Vorresti ricevere notifiche sulle strutture delle tue citta' preferite?\n" +
                                "Scrivi SI se hai interesse, qualsiasi cosa altrimenti:");
                        notificationServiceChoice = console.readLine().trim();
                        if (notificationServiceChoice.equalsIgnoreCase("si")) {
                            System.out.println("Quali sono le tue citta' preferite? Scrivi qui il loro nome, separato da virgola: ");
                            favouriteCities = console.readLine().trim();

                            //registrazioneServizioNotifica
                            System.out.println(remoteServer.notificationService(arguments[1], favouriteCities));
                        }
                        else System.out.println("Non riceverai notifiche sulle strutture di nessuna citta'!");
                        break;
                    case "login":
                        boolean printResult = true;
                        username = arguments[1];
                        result = standardCommand(command, socketChannel);
                        while (result.equals(ErrorCodes.WRONG_PSWD)) {
                            System.out.println(ErrorCodes.WRONG_PSWD + ", ritenta il login riscrivendo il comando completo (login, ..., ...)\n" +
                                    "oppure digita qualsiasi altra parola per annullare l'operazione\n");
                            command = console.readLine().trim();
                            arguments = command.split(",");
                            //Rimuovo gli spazi all'interno della stringa
                            for (int i = 0; i < arguments.length; i++) {
                                arguments[i] = arguments[i].trim();
                            }
                            //Formatto anche il comando iniziale togliendo gli spazi
                            StringBuilder resultBuilderForLogin = new StringBuilder();
                            for (int i = 0; i < arguments.length - 1; i++) {
                                resultBuilderForLogin.append(arguments[i]);
                                resultBuilderForLogin.append(",");
                            }
                            command = resultBuilderForLogin + arguments[arguments.length - 1];
                            arguments = command.split(",");
                            if (Objects.equals(arguments[0], "login")) result = standardCommand(arguments[0] + "," + arguments[1] + "," + arguments[2], socketChannel);
                            else{
                                printResult = false;
                                this.printMenu();
                                break;
                            }
                        }
                        if(printResult) System.out.println(result);
                        break;
                    case "cerca":
                    case "citta":
                        result = standardCommand(command, socketChannel);
                        System.out.println(result);
                        break;
                    case "recensione":
                    case "badges":
                    case "logout":
                        System.out.println("Ripeti il tuo username:\n"); //gestisco piu utenti connessi
                        username = console.readLine().trim();
                        result = commandWithUsername(command, socketChannel, username);
                        System.out.println(result);
                        break;
                    case "esci":
                        result = standardCommand(command, socketChannel);
                        System.out.println(result);
                        choice = "esci";
                        break;
                    case "menu":
                        printMenu();
                        break;
                    default:
                        System.out.println("Scelta non valida");
                        break;
                }
            }

            socketChannel.close();
            //cancello la registrazione per callback
            remoteServer.unregisterForCallback(stubCallBack);
            System.exit(0); //chiusura client

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void printMenu() {
        System.out.println("\nBenvenuti in HOTELIER: Recensisci anche te!");
        System.out.println("Scegli cosa fare, tra le funzionalita' disponibili, " +
                "scrivi il nome di quella selezionata seguito da i parametri necessari " +
                "separati da virgola, " +
                "sono specificati tra ()!\n" +
                "Esempio per eseguire il login: login, username, password\n");
        System.out.println("->registrazione : Registrazione nuovo utente (username, password)\n" +
                "->login : Login utente registrato (username, password)\n" +
                "->cerca : Cerca un hotel in una determinata citta'(nomeHotel, nomeCitta)\n" +
                "->citta : Cerca gli hotel di una citta' (nomeCitta)\n" +
                "->recensione : Nuova recensione hotel (nomeHotel, nomeCitta', globalScores, singleScores)\n" +
                "\t(singleScores: inserire 4 valori, uno per ogni categoria: pulizia, posizione, servizi, qualita'/prezzo)\n" +
                "->badges : Guarda i tuoi badges\n" +
                "->logout : Logout\n" +
                "->esci : Esci\n" +
                "->menu : Stampa di nuovo il menu'\n");
    }

    private String standardCommand(String command, SocketChannel socketChannel) throws IOException {
        // Invio il comando al server
        try {
            command = command.trim();
            socketChannel.write(ByteBuffer.wrap(command.getBytes()));
        } catch (Exception ex) {
            System.out.println(ex);
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(clientConfig.getDim()); // Allocazione del buffer per la risposta

        // Leggi la risposta dal server nel buffer
        int bytesRead = 0;
        try {
            bytesRead = socketChannel.read(byteBuffer);
        } catch (Exception ex) {
            System.out.println(ex);
        }

        // Controlla se è stata letta una risposta
        if (bytesRead == -1) {
            throw new IOException("Connection closed by server");
        }

        byteBuffer.flip(); // Passa in modalità lettura

        //Creo un array di byte della dimensione della risposta
        byte[] responseBytes = new byte[byteBuffer.limit()];

        //Copio i byte della risposta nel nuovo array
        byteBuffer.get(responseBytes);

        //pulisco il buffer
        byteBuffer.clear();

        //Converto i byte in una stringa
        String response = new String(responseBytes);

        return response.trim();
    }


    private String commandWithUsername(String command, SocketChannel socketChannel, String username) throws Exception { //socketchannel.write lancia IOException
        if (!Objects.equals(command, "esci") && username.isEmpty() || username == null) {
            return "Devi prima aver effettuato il login!";
        }
        command = command.trim();
        String commandAndUsername = command + "," + username; //creo unica stringa con comando e username per mandarla al server
        socketChannel.write(ByteBuffer.wrap(commandAndUsername.getBytes())); //invio il comando e lo username al server
        ByteBuffer byteBuffer = ByteBuffer.allocate(clientConfig.getDim()); //allocazione del buffer per lettura, anche della risposta

        socketChannel.read(byteBuffer);
        byteBuffer.flip(); //passo in modalita' lettura

        //pulisco il buffer
        byteBuffer.clear();

        return new String(byteBuffer.array()).trim();
    }

    //Metodo per la ricezione dei messaggi multicast
    public void receiveUDPMessage() throws IOException {
        byte[] buffer = new byte[clientConfig.getDim()];
        //try-with-resources chiude automaticamente MulticastSocket
        try (MulticastSocket socket = new MulticastSocket(clientConfig.getMulticastPort())) {
            InetAddress group = InetAddress.getByName(clientConfig.getMulticastIP());
            socket.joinGroup(group);
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength());
                List<String> res = Utils.hotelInfoExtractor(msg);

                for (String info : res) {
                    if (info.equals(ErrorCodes.INVALID_HOTEL_INFOS_FORMAT)){
                        break;
                    }
                    String[] infos = info.split(";");
                    //Nome citta'
                    String cityName = infos[0];
                    //Nome hotel
                    String hotelName = infos[1];

                    synchronized (LocalRankings) {
                        LocalRankings.put(cityName, hotelName);
                    }
                    if ("OK".equals(msg)) {
                        System.out.println("No more message. Exiting : " + msg);
                        break;
                    }
                }
                System.out.println(msg);
                //Non abbandono il gruppo multicast fino a quando non interrompo l'esecuzione
                //socket.leaveGroup(group);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //METODI CALLBACK
    //Si occupa di avvisare di un aggiornamento nel ranking
    @Override
    public void notifyEvent() throws RemoteException {
        System.out.println(">>>[AGGIORNAMENTO RANKING LOCALE HOTEL]<<<");
    }
}
