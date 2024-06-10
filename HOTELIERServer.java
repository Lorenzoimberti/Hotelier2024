import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class HOTELIERServer extends RemoteServer implements RemoteServerInterface {

    //Creo collezioni sincronizzate per essere thread-safe
    private Map<String, User> usersDB = Collections.synchronizedMap(new HashMap<>());//hashmap per gli utenti registrati
    private Map<Hotel, List<Review>> hotelsReviews = Collections.synchronizedMap(new HashMap<>()); //hasmap per le recensioni degli hotel
    private Map<User, List<String>> notificationsSubscribers = Collections.synchronizedMap(new HashMap<>()); //hasmap per le iscrizioni degli utenti
    private List<NotifyEventInterface> clients = Collections.synchronizedList(new ArrayList<>()); //lista dei clients per rmi
    private List<Hotel> allHotels = Collections.synchronizedList(new ArrayList<>()); //lista di tutti gli hotel dopo deserializzazione
    private List<String> allCities = Collections.synchronizedList(new ArrayList<>()); //lista di tutte le citta in cui si trovano gli hotel
    private Map<String, List<HotelWithRankingDto>> localRankings = Collections.synchronizedMap(new HashMap<>()); //Hashmap per mantenere i ranking di tutte le citta'
    private static ServerConfig serverConfig = new ServerConfig(); //Classe per i parametri di configurazione del server
    private static String serverConfigFile = "ServerConfig.json"; //in locale /src
    private final Lock hotelsLock = new ReentrantLock(); //lock per gestire l'accesso a allHotels
    private final Lock usersLock = new ReentrantLock(); //lock per gestire l'accesso a UsersDB
    private final Lock reviewsLock = new ReentrantLock(); //lock per gestire l'accesso a HotelsReviews

    public HOTELIERServer() throws RemoteException {
        super();
    }

    public void start(int timeForRankingUpdate) {

        //Recupero delle configurazioni
        try {
            serverConfig = Utils.getServerConfig(serverConfigFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Creazione del threadpool
        ExecutorService executor = Executors.newFixedThreadPool(serverConfig.getThreadpoolSize());

        //Task per effettuare l'update dei ranking
        Runnable rankingUpdaterTask = () -> {
            try {
                //Aspetto che il client si avii
                Thread.sleep(serverConfig.getRankingUpdaterDefaultWaitTime());
                while (true) {
                    localRankingUpdater(); //Invia il ranking aggiornato
                    //converto i secondi in millisecondi
                    Thread.sleep(timeForRankingUpdate * 1000L); //Ritardo prima del successivo aggiornamento
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        //Task per aggiornamento dei file json contenenti utenti, recensioni e hotel
        Runnable jsonFileUpdaterTask = () -> {
            try {
                while (true) {
                    //Creo o sovrascrivo i file
                    Utils.jsonCreator(serverConfig.getRegisteredUsersJsonFileName(), Arrays.asList(usersDB.values().toArray()));
                    Utils.jsonCreator(serverConfig.getReviewsJsonFileName(), Arrays.asList(hotelsReviews.values().toArray()));
                    Utils.jsonCreator(serverConfig.getHotelsJsonPath(), allHotels);

                    Thread.sleep(serverConfig.getDbUpdaterWaitTime()); //Ritardo prima del successivo aggiornamento (5 sec)
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        //INIZIALIZZAZIONE DATABASES
        //Prendo gli hotel dal file json
        allHotels = Utils.hotelsJsonConverter(serverConfig.getHotelsJsonPath(), hotelsLock);
        //Prendo tutte le citta in cui sono presenti hotel
        synchronized (allHotels) {
            for (Hotel h : allHotels) {
                synchronized (allCities) {
                    if (!(allCities.contains(h.getCity()))) {
                        allCities.add(h.getCity());
                    }
                }
            }
        }
        //Prendo users e recensioni gia' esistenti dai rispettivi file Json, se esistenti
        //Users
        File users = new File(serverConfig.getRegisteredUsersJsonFileName());
        if (users.exists()) {
            //se non e' un array vuoto
            if (!Utils.containsEmptyArray(serverConfig.getRegisteredUsersJsonFileName())) {
                //Prendo users dal file json
                List<User> existingUsers = Utils.usersJsonConverter(serverConfig.getRegisteredUsersJsonFileName(), usersLock);
                if (!(existingUsers == null) && !existingUsers.isEmpty()) {

                    //Metto gli users nel DB
                    for (User user : existingUsers) {
                        user.setIsLogged(false); //setto logged a false, necessario rieffettuare il login
                        synchronized (usersDB) {
                            usersDB.put(user.getUsername(), user);
                        }
                    }
                }
            } else Utils.emptyFile(serverConfig.getRegisteredUsersJsonFileName()); //se e' presente solo un array vuoto, svuoto il file
        }
        //Recensioni
        File reviews = new File(serverConfig.getReviewsJsonFileName());
        if (reviews.exists()) {
            //se non e' un array vuoto
            if (!Utils.containsEmptyArray(serverConfig.getReviewsJsonFileName())) {
                //Prendo recensioni dal file json
                List<Review> existingReviews = Utils.reviewsJsonConverter(serverConfig.getReviewsJsonFileName(), reviewsLock);
                if (!(existingReviews == null) && !existingReviews.isEmpty()) {
                    //Lista che mi servira' per i diversi hotel
                    List<Integer> allDifferentHotelIds = new ArrayList<>();

                    //Prendo tutti gli hotel differenti tramite il loro id
                    for (Review review : existingReviews) {
                        int hotelId = review.getHotel().getId();
                        if (!allDifferentHotelIds.contains(hotelId)) {
                            allDifferentHotelIds.add(hotelId);
                        }
                    }
                    //Per ogni hotel, prendo tutte le recensioni che lo riguardano
                    Hotel myHotel = null;
                    for (Integer hotelId : allDifferentHotelIds) {
                        synchronized (allHotels) {
                            for (Hotel hotel : allHotels) {
                                if (hotel.getId() == hotelId) {
                                    myHotel = hotel;
                                    break;
                                }
                            }
                        }
                        if (myHotel == null) continue;
                        List<Review> reviewsForHotel = new ArrayList<>();
                        for (Review review : existingReviews) {
                            if (review.getHotel().getId() == hotelId) {
                                reviewsForHotel.add(review);
                            }
                        }
                        //Aggiungo l'hotel e le sue recensioni al DB
                        synchronized (hotelsReviews) {
                            hotelsReviews.put(myHotel, reviewsForHotel);
                        }
                        //Aggiorno il punteggio dell'hotel
                        Rating thisHotelRatings = calculateHotelRatings(myHotel);
                        //Aggiorno hotel a DB
                        //Hotel thisHotel = allHotels.get(myHotel.getId());
                        myHotel.setRate(myHotel.getRate());
                        myHotel.setRatings(thisHotelRatings);
                    }
                }
            } else Utils.emptyFile(serverConfig.getReviewsJsonFileName()); //se e' presente solo un array vuoto, svuoto il file
        }

        //Inizializzo i ranking locali per la prima volta (senza aspettare l'avvio del client)
        try {
            localRankingUpdater();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Eseguo i task di aggiornamento nel threadpool
        executor.submit(rankingUpdaterTask);
        executor.submit(jsonFileUpdaterTask);

        //Inizializzo lista degli utenti che hanno attivato le notifiche
        synchronized (usersDB) {
            for (User user : usersDB.values()) {
                synchronized (notificationsSubscribers) {
                    notificationsSubscribers.put(user, user.getCitiesForNotifications());
                }
            }
        }

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) //Apertura socket non bloccante
        {
            serverSocketChannel.socket().bind(new InetSocketAddress(serverConfig.getTcpPort())); //Server si mette in ascolto sulla porta indicata
            serverSocketChannel.configureBlocking(false);
            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); //Registrazione dei canali

            while (true) {
                if (selector.select() == 0) continue;

                Set<SelectionKey> selectedKeys = selector.selectedKeys(); //Insieme delle chiavi corrispondenti ai canali pronti
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()) {//Se è presente una connessione da parte di un client l'accetto e registro la sua chiave per la lettura
                        ServerSocketChannel socket = (ServerSocketChannel) key.channel();
                        SocketChannel client = socket.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                    }

                    if (key.isReadable()) {//Sono presenti dati da leggere da parte di un client
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer request = ByteBuffer.allocate(serverConfig.getDim());
                        client.read(request);

                        String command = new String(request.array()).trim();
                        String[] args = command.split(",");//divisione del comando inviato per passaggio ai metodi
                        String reply = null;

                        switch (args[0]) {
                            case "registrazione":
                                if (args.length != 3) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    reply = this.registration(args[1], args[2]);
                                }
                                sendReply(reply, client);
                                break;
                            case "login":
                                if (args.length != 3) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    reply = this.login(args[1], args[2]);
                                }
                                sendReply(reply, client);
                                break;
                            case "cerca":
                                if (args.length != 3) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    reply = this.searchWithCity(args[1], args[2]);
                                }
                                sendReply(reply, client);
                                break;
                            case "citta":
                                if (args.length != 2) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    reply = this.hotelsByCity(args[1]);
                                }
                                sendReply(reply, client);
                                break;
                            case "recensione":
                                if (args.length != 9) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    synchronized (usersDB) {
                                        if (!usersDB.containsKey(args[8]) || !usersDB.get(args[8]).isLogged) {
                                            sendReply(ErrorCodes.REVIEW_MUST_BE_LOGGED, client);
                                            break;
                                        } else {
                                            reply = this.newReview(args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                                            User currentUser = usersDB.get(args[8]);
                                            //aggiorno il livello dell'utente che ha fatto la recensione
                                            currentUser.setNumberOfReviews((currentUser.getNumberOfReviews()) + 1);
                                            userLevelUpdater(currentUser);
                                        }
                                    }
                                }
                                sendReply(reply, client);
                                break;
                            case "badges":
                                if (args.length != 2) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    reply = this.viewBadges(args[1]);
                                }
                                sendReply(reply, client);
                                break;
                            case "logout":
                                if (args.length != 2) {
                                    sendReply(ErrorCodes.WRONG_PARAMETERS_NUMBER, client);
                                    break;
                                } else {
                                    reply = this.logout(args[1]);
                                }
                                sendReply(reply, client);
                                break;
                            case "esci":
                                sendReply("CHIUSURA CLIENT", client);
                                try {
                                //Chiusura della SocketChannel e cancellazione della chiave associata
                                key.cancel();
                                key.channel().close();
                                client.close();
                                }catch (Exception e) {
                                    throw e;
                                }
                                break;
                            default:
                                sendReply(ErrorCodes.DEFAULT_SERVER_ERROR, client);
                        }
                    }
                }
            }
        } catch (IOException e) {
            //se c'e' un errore, effettuo il logout di tutti gli utenti collegati
            this.logoutAllUsers();
            try {
                //aspetto che si aggiorni il db
                Thread.sleep(serverConfig.getDbUpdaterWaitTime());
                //Termino l'esecuzione del threadpool
                executor.shutdown();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        } finally {
            //Termino l'esecuzione del threadpool
            executor.shutdownNow();
        }
    }


    //METODI SERVER
    private void sendReply(String reply, SocketChannel client) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(reply.getBytes());
        while (byteBuffer.hasRemaining()) {
            client.write(byteBuffer);
        }
    }

    public boolean credentialsCheck(String username, String password) {
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public String registration(String username, String password)throws RemoteException {
        synchronized (usersDB) {
            if (!credentialsCheck(username, password) || usersDB.containsKey(username)) {
                return "Impossibile effettuare la registrazione: utente gia' registrato o parametri non validi";
            }
            User newUser = new User();
            newUser.setUsername(username);
            newUser.setPassword(password);
            usersDB.put(username, newUser);
        }
        return "Registrazione avvenuta con successo, " + username + "\n";
    }

    private String login(String username, String password) {
        if (!credentialsCheck(username, password)) {
            return "Username o password non validi\n";
        }
        synchronized (usersDB) {
            if (usersDB.containsKey(username)) { //se username presente
                User utente = usersDB.get(username);
                if (utente.getPassword().equals(password) && !utente.getIsLogged()) { //se la password coincide e l'utente non e' gia' loggato
                    utente.isLogged = true;
                    return "Login effettuato correttamente, " + username;
                }
                return ErrorCodes.WRONG_PSWD;
            }
        }
        return "Errore nel login\n";
    }

    private String searchWithCity(String hotelName, String city) {
        synchronized (allHotels) {
            for (Hotel hotel : allHotels) {
                if (Objects.equals(hotel.getName().trim().toLowerCase(), hotelName.trim().toLowerCase()) && Objects.equals(hotel.getCity().trim().toLowerCase(), city.trim().toLowerCase())) { //cerco per nome e citta'
                    Rating hotelRating = hotel.getRatings();
                    //creo stringa da restituire con StringBuilder
                    StringBuilder resultBuilder = new StringBuilder();
                    resultBuilder.append(hotel.getName())
                            .append(": ")
                            .append(hotel.getDescription())
                            .append("\n")
                            .append("citta': ")
                            .append(hotel.getCity())
                            .append("\n")
                            .append("numero di telefono: ")
                            .append(hotel.getPhone())
                            .append("\n")
                            .append("servizi offerti: ")
                            .append(Arrays.toString(hotel.getServices()))
                            .append("\n")
                            .append("voto totale: ")
                            .append(String.format("%.2f", hotel.getRate()))
                            .append("\n")
                            .append("voti specifici: ")
                            .append(" Pulizia: ")
                            .append(hotelRating.getCleaning())
                            .append(" Posizione: ")
                            .append(hotelRating.getPosition())
                            .append(" Servizi: ")
                            .append(hotelRating.getServices())
                            .append(" Rapporto Qualita'-Prezzo: ")
                            .append(hotelRating.getQuality())
                            .append("\n");
                    String result = resultBuilder.toString();
                    return result;
                }
            }
        }
        return "Errore nella ricerca dell'hotel: cambiare struttura o citta'";
    }

    private String hotelsByCity(String city) {
        List<Hotel> hotels = new ArrayList<>();
        synchronized (allHotels) {
            for (Hotel hotel : allHotels) {
                if (Objects.equals(hotel.getCity().trim().toLowerCase(), city.trim().toLowerCase())) { //cerco per citta'
                    hotels.add(hotel);
                }
            }
        }

        // Ordinamento della lista in modo decrescente (lambda expression)
        hotels.sort((h1, h2) -> Double.compare(h2.getRate(), h1.getRate()));

        //creo stringa da restituire con StringBuilder
        StringBuilder resultBuilder = new StringBuilder();
        for (Hotel hotel : hotels) {
            resultBuilder.append(hotel.getName())
                    .append(": ")
                    .append(hotel.getDescription())
                    .append(";")
                    .append("\n");
        }
        String result = resultBuilder.toString();

        return result.isEmpty() ? "Nessun hotel presente a " + city : result;
    }

    private String newReview(String hotelName, String city, String globalScore, String cleaning, String position, String services, String quality) throws IOException {
        //Eseguo il parsing delle stringhe, eliminando eventuali spazi iniziali, per ottenere i valori numerici
        double globalScoreDouble = Double.parseDouble(globalScore.trim());
        int cleaningInt = Integer.parseInt(cleaning.trim());
        int positionInt = Integer.parseInt(position.trim());
        int servicesInt = Integer.parseInt(services.trim());
        int qualityInt = Integer.parseInt(quality.trim());

        List<Review> reviews = null; //lista delle recensioni

        synchronized (allHotels) {
            for (Hotel hotel : allHotels) {
                if (Objects.equals(hotel.getName().trim().toLowerCase(), hotelName.trim().toLowerCase()) && Objects.equals(hotel.getCity().trim().toLowerCase(), city.trim().toLowerCase())) { //cerco per nome e citta'
                    //aggiungo un nuovo rating all'hotel
                    Rating newRating = new Rating();
                    //setto i valori
                    newRating.setCleaning(cleaningInt);
                    newRating.setPosition(positionInt);
                    newRating.setServices(servicesInt);
                    newRating.setQuality(qualityInt);

                    //Aggiungo una nuova recensione all'hotel
                    //trovo l'hotel nelle recensioni, altrimenti inserisco
                    synchronized (hotelsReviews) {
                        if (hotelsReviews.containsKey(hotel)) {
                            reviews = hotelsReviews.get(hotel);
                        } else {
                            reviews = new ArrayList<>();
                            hotelsReviews.put(hotel, reviews);
                            reviews = hotelsReviews.get(hotel);
                        }
                    }
                    //creo e inizializzo la nuova review
                    Review newReview = new Review();
                    newReview.setRatings(newRating);
                    newReview.setOverall(globalScoreDouble);
                    newReview.setDateOfCreation(LocalDateTime.now());
                    newReview.setHotel(hotel);
                    //aggiungo la nuova review alla lista
                    reviews.add(newReview);

                    //devo calcolare ed assegnare il nuovo rating finale
                    Rating finalRating = calculateHotelRatings(hotel);
                    hotel.setRatings(finalRating);
                }
            }
        }
        if (reviews == null || reviews.isEmpty()) return "Errore nella ricerca dell'hotel: cambiare struttura o citta'";
        return "Recensione per l'hotel: " + hotelName + ", avvenuta correttamente";
    }

    private Rating calculateHotelRatings(Hotel hotel) {

        List<Review> myHotelRatings;

        synchronized (hotelsReviews) {
            //cerco l'hotel nella collezione
            myHotelRatings = hotelsReviews.get(hotel);
        }

        //calcolo i punteggi dell'hotel in base alle recensioni
        Rating finalRating = new Rating();
        int numOfReviews = myHotelRatings.size();

        double doubleCleaningVal = 0;
        double doublePositionVal = 0;
        double doubleServicesVal = 0;
        double doubleQualityVal = 0;

        double finalRate = 0;

        for (Review review : myHotelRatings) {
            //recupero i voti della recensione
            int cleaning = review.getRatings().getCleaning();
            int position = review.getRatings().getPosition();
            int services = review.getRatings().getServices();
            int quality = review.getRatings().getQuality();
            //aggiorno i valori totali
            doubleCleaningVal += cleaning;
            doublePositionVal += position;
            doubleServicesVal += services;
            doubleQualityVal += quality;

            //in base a quanto e' vecchia la recensione, ne diminuisco il peso
            if (LocalDateTime.now().minusMonths(1).isBefore(review.getDateOfCreation())) {
                review.setReviewWeight(0.20);
            } else if (LocalDateTime.now().minusMonths(3).isBefore(review.getDateOfCreation())) {
                review.setReviewWeight(0.15);
            } else if (LocalDateTime.now().minusMonths(6).isBefore(review.getDateOfCreation())) {
                review.setReviewWeight(0.10);
            } else {
                review.setReviewWeight(0.05);
            }

            //in base al numero di recensioni dell'hotel, modifico il peso delle recensioni:
            //piu' sono, meno sara' incisivo
            double reviewWeight = review.getReviewWeight();
            if (numOfReviews < 5) reviewWeight += 0.09;
            else if (numOfReviews < 10) reviewWeight += 0.06;
            else if (numOfReviews < 15) reviewWeight += 0.03;

            review.setReviewWeight(reviewWeight);

            //il voto finale sara' la somma dei voti delle singole categorie piu' il peso della recensione
            finalRate += ((cleaning + position + services + quality) + reviewWeight) / 4;
        }

        //ottengo il rating finale, castando i risultati a interi
        finalRating.setCleaning((int) (doubleCleaningVal / numOfReviews));
        finalRating.setPosition((int) (doublePositionVal / numOfReviews));
        finalRating.setServices((int) (doubleServicesVal / numOfReviews));
        finalRating.setQuality((int) (doubleQualityVal / numOfReviews));

        //rate finale dato dai 4 parametri
        double rateToPass = finalRate / numOfReviews;
        //Setto al massimo il valore 5.0
        hotel.setRate(Math.min(rateToPass, 5.0));

        return finalRating;
    }

    //Metodo che calcola il ranking degli hotel per ogni citta' e invia periodicamente agli utenti gli aggiornamenti
    private void localRankingUpdater() throws IOException {

        synchronized (localRankings) {
            //se c'e' gia' stata una inizializzazione dei ranking
            if (!localRankings.isEmpty()) {
                for (String city : allCities) {
                    //lista degli hotel che hanno subito una variazione nel ranking della citta'
                    List<HotelWithRankingDto> modifiedHotels = new ArrayList<>();
                    List<HotelWithRankingDto> hotelsWithSameCity = localRankings.get(city);
                    if (hotelsWithSameCity == null || hotelsWithSameCity.isEmpty()) continue;
                    //ordino la lista in modo decrescente rispetto al rating,
                    //aggiorno la posizione di ogni hotel e la inserisco
                    hotelsWithSameCity.sort((h1, h2) -> Double.compare(h2.getRating(), h1.getRating()));

                    //prendo l'ordine del db, aggiornato dalle piu' recenti recensioni
                    Map<String, List<HotelWithRankingDto>> actualHotelPositions = orderSameCityHotels();
                    List<HotelWithRankingDto> thisCityHotels = actualHotelPositions.get(city);

                    if (!(thisCityHotels == null || thisCityHotels.isEmpty())) {
                        //aggiungo alla lista gli hotel che hanno subito una variazione
                        for (int i = 0; i < hotelsWithSameCity.size(); i++) {
                            if (!(thisCityHotels.get(i).getHotelId() == hotelsWithSameCity.get(i).getHotelId())) {
                                modifiedHotels.add(thisCityHotels.get(i));
                            }
                        }
                    }
                    if (modifiedHotels.isEmpty()) continue;

                    //prendo gli user loggati con interesse per la citta' attuale
                    List<User> usersWithThisCityNotification = new ArrayList<>();
                    for (User user : usersDB.values()) {
                        if (user.getIsLogged() && !(user.getCitiesForNotifications() == null) && !user.getCitiesForNotifications().isEmpty()) {
                            for (String userCity : user.getCitiesForNotifications()) {
                                if (userCity.trim().toLowerCase().equals(city.trim().toLowerCase())) {
                                    usersWithThisCityNotification.add(user);
                                }
                            }
                        }
                    }
                    if (usersWithThisCityNotification.isEmpty()) continue;

                    //notifico ad ogni utente interessato l'aggiornamento nella classifica del ranking locale
                    StringBuilder result = new StringBuilder(); //preparo la risposta
                    for (User user : usersWithThisCityNotification) {
                        for (HotelWithRankingDto dto : modifiedHotels) {
                            for (Hotel hotel : allHotels) {
                                if (dto.getHotelId() == hotel.getId()) {
                                    String modifiedHotel = ("L'hotel: ") + hotel.getName() +
                                            " e' adesso in posizione " + dto.getRankingPosition() + " nella citta': " + dto.getHotelCity() + "\n";
                                    result.append(modifiedHotel);
                                }
                            }
                        }
                        result.append("Gli hotel non in elenco hanno mantenuto la loro posizione.\n");
                        //Invio il messaggio
                        sendUDPMessage(result.toString());
                    }
                    //aggiorno il ranking
                    localRankings.put(city, thisCityHotels);
                }
            } else {
                //prendo l'ordine del db
                Map<String, List<HotelWithRankingDto>> actualHotelPositions = orderSameCityHotels();

                for (String city : actualHotelPositions.keySet()) {
                    List<HotelWithRankingDto> hotelsWithSameCity = new ArrayList<>();
                    for (Hotel h : allHotels) {
                        HotelWithRankingDto newHotelDto = new HotelWithRankingDto();
                        if (h.getCity().equals(city)) {
                            newHotelDto.setHotelId(h.getId());
                            newHotelDto.setHotelCity(city);
                            newHotelDto.setRating(h.getRate());
                            hotelsWithSameCity.add(newHotelDto);
                        }
                    }
                    if (hotelsWithSameCity.isEmpty()) continue;
                    //ordino la lista in modo decrescente rispetto al rating,
                    //aggiorno la posizione di ogni hotel e la inserisco
                    hotelsWithSameCity.sort((h1, h2) -> Double.compare(h2.getRating(), h1.getRating()));
                    //assegno le posizioni nel ranking;
                    int i = 1;
                    for (HotelWithRankingDto dto : hotelsWithSameCity) {
                        dto.setRankingPosition(i);
                        i++;
                    }

                    //Aggiungo la lista ordinata ai rankings
                    localRankings.put(city, hotelsWithSameCity);
                }
            }
        }
    }

    private Map<String, List<HotelWithRankingDto>> orderSameCityHotels() {

        Map<String, List<HotelWithRankingDto>> rankings = new HashMap<>();

        synchronized (allCities) {
            for (String city : allCities) {
                List<HotelWithRankingDto> hotelsWithSameCity = new ArrayList<>();
                synchronized (allHotels) {
                    for (Hotel h : allHotels) {
                        if (h.getCity().equals(city)) {
                            HotelWithRankingDto newHotelDto = new HotelWithRankingDto();
                            newHotelDto.setHotelId(h.getId());
                            newHotelDto.setHotelCity(city);
                            newHotelDto.setRating(h.getRate());
                            hotelsWithSameCity.add(newHotelDto);
                        }
                    }
                }
                if (hotelsWithSameCity.isEmpty()) continue;
                //ordino la lista in modo decrescente rispetto al rating,
                //aggiorno la posizione di ogni hotel e la inserisco
                hotelsWithSameCity.sort((h1, h2) -> Double.compare(h2.getRating(), h1.getRating()));
                for (int i = 0; i < hotelsWithSameCity.size(); i++) {
                    hotelsWithSameCity.get(i).setRankingPosition(i + 1);
                }
                rankings.put(city, hotelsWithSameCity);
            }
        }

        return rankings;
    }


    private void userLevelUpdater(User user) {
        int numOfRev = user.getNumberOfReviews();
        if (numOfRev <= 3) user.setLevel(UserLevel.Recensore);
        else if (numOfRev <= 8) user.setLevel(UserLevel.RecensoreEsperto);
        else if (numOfRev <= 13) user.setLevel(UserLevel.Contributore);
        else if (numOfRev <= 20) user.setLevel(UserLevel.ContributoreEsperto);
        else user.setLevel(UserLevel.ContributoreSuper);
    }

    private String viewBadges(String username) {
        synchronized (usersDB) {
            //se l'utente e' loggato ritorno il suo livello
            if (usersDB.containsKey(username) && usersDB.get(username).isLogged) {
                User currentUser = usersDB.get(username);
                if (currentUser.getLevel() == null) {
                    return "Non hai ancora aggiunto recensioni, guadagna subito il tuo primo badge e sali di livello!";
                }
                return "Attualmente hai raggiunto il livello di: " + currentUser.getLevel().name() + ", con " + currentUser.getNumberOfReviews() + " recensioni fatte";
            }
        }
        return "Per poter visionare i tuoi traguardi devi prima aver effettuato il login";
    }

    private String logout(String username) {
        //se l'utente e' loggato eseguo il logout
        synchronized (usersDB) {
            if (usersDB.containsKey(username) && usersDB.get(username).isLogged) {
                User currentUser = usersDB.get(username);
                currentUser.isLogged = false;
                return "Logout effettuato correttamente";
            }
        }
        return "Per poter effettuare il logout devi prima aver effettuato il login";
    }

    //Metodo per invio messaggi in Multicast
    public void sendUDPMessage(String message) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        InetAddress group = InetAddress.getByName(serverConfig.getMulticastIP());
        byte[] msg = message.getBytes();
        DatagramPacket packet = new DatagramPacket(msg, msg.length, group, serverConfig.getMulticastPort());
        synchronized (clients){
            for (NotifyEventInterface client : clients){
                try{
                    client.notifyEvent();
                }catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }
        socket.send(packet);
        System.out.println("Spedito messaggio al client");
        socket.close();
    }

    @Override
    public String notificationService(String username, String cities) throws RemoteException {
        User currentUser;
        //recupero l'user con l'username passato
        synchronized (usersDB) {
            currentUser = usersDB.get(username);
            if (currentUser == null) return "Utente non trovato";
        }
        //ottengo la lista delle citta' con la funzione split
        String[] allCities = cities.split(",");
        //tolgo gli spazi
        for (int i = 0; i < allCities.length; i++) {
            allCities[i] = allCities[i].trim();
        }

        synchronized (notificationsSubscribers) {
            //Se non e' gia' iscritto oppure ha specificato citta' differenti precedentemente
            List<String> existingCities = notificationsSubscribers.get(currentUser);

            if (existingCities == null || existingCities.isEmpty() || !(allCities.equals(notificationsSubscribers.get(currentUser)))) {
                notificationsSubscribers.put(currentUser, Arrays.asList(allCities));
                //Aggiungo le citta' di interesse all'utente
                currentUser.setCitiesForNotifications(Arrays.asList(allCities));
                return "Registrazione al servizio di notifica avvenuto correttamente.";
            } else {
                return "Utente gia' iscritto al servizio";
            }
        }
    }

    private void logoutAllUsers() {
        synchronized (usersDB){
            for (User user : usersDB.values()) {
                user.setIsLogged(false);
            }
        }
    }

    //METODI CALLBACK
    @Override
    public void registerForCallback(NotifyEventInterface ClientInterface) throws RemoteException {
        if (!clients.contains(ClientInterface)) {
            clients.add(ClientInterface);
        }
        System.out.println("Nuovo client registrato");
    }

    @Override
    public void unregisterForCallback(NotifyEventInterface Client) throws RemoteException {
        if (clients.remove(Client)) {
            System.out.println("Client rimosso");
        } else {
            System.out.println("Impossibile rimuovere l'iscrizione del client");
        }
    }
}
