import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static boolean containsEmptyArray(String filename) {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(filename);

        try {
            //Leggo il contenuto del file come JsonNode
            JsonNode rootNode = objectMapper.readTree(file);

            //Verifico se il nodo radice è un array vuoto
            return rootNode.isArray() && rootNode.size() == 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void emptyFile(String filename) {
        try {
            FileWriter writer = new FileWriter(filename);
            writer.write(""); // Scrive una stringa vuota nel file
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Metodi per file json
    public static List<Hotel> hotelsJsonConverter(String filename, Lock hotelsLock) {
        List<Hotel> hotels = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        //registro modulo per gestire LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());
        File file = new File(filename);
        try {
            hotelsLock.lock(); //acquisisco la lock prima di accedere alla lista
            Hotel[] hotelArray = objectMapper.readValue(file, Hotel[].class);
            hotels.addAll(Arrays.asList(hotelArray));

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            hotelsLock.unlock(); //rilascio la lock
        }
        return hotels;
    }
    public static List<User> usersJsonConverter(String filename, Lock usersLock) {
        List<User> users = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        //registro modulo per gestire LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());
        File file = new File(filename);
        try {
            usersLock.lock(); //acquisisco la lock prima di accedere alla lista
            User[] userArray = objectMapper.readValue(file, User[].class);
            users.addAll(Arrays.asList(userArray));

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            usersLock.unlock(); //rilascio la lock
        }
        return users;
    }

    public static List<Review> reviewsJsonConverter(String filename, Lock reviewsLock) {
        List<Review> reviews = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        //registro modulo per gestire LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());
        File file = new File(filename);
        try {
            reviewsLock.lock();
            //Deserializzo il json in un array di array di Review
            Review[][] reviewsArray = objectMapper.readValue(file, Review[][].class);
            for (Review[] reviewArray : reviewsArray) {
                Collections.addAll(reviews, reviewArray);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            reviewsLock.unlock(); //rilascio la lock
        }
        return reviews;
    }

    //Metodo per creare un file json
    public static <T> void jsonCreator(String fileName, List<T> entities) {
        ObjectMapper objectMapper = new ObjectMapper();
        //registro modulo per gestire LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());

        //Imposto l'indentazione per una formattazione più leggibile
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Convertire l'entità in formato JSON e salvarla su file
        try {
            objectMapper.writeValue(new File(fileName), entities);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Metodo per estrarre nome e citta' di appartenenza di un hotel
    public static List<String> hotelInfoExtractor(String infos){

        //String result = "";
        List<String> results = new ArrayList<>();

        //Pattern regex per estrarre il nome dell'hotel e il nome della citta'
        Pattern pattern = Pattern.compile("L'hotel: (.+), e' adesso primo nella citta': (.+)");
        Matcher matcher = pattern.matcher(infos);

        //Verifico se il pattern corrisponde alla stringa
        while (matcher.find()) { //era if
            //Estrae il nome dell'hotel e il nome della citta'
            String hotelName = matcher.group(1);
            String cityName = matcher.group(2);

            // Aggiunge il risultato alla lista
            results.add(cityName + ";" + hotelName);
        }

        if (results.isEmpty()) {
            results.add(ErrorCodes.INVALID_HOTEL_INFOS_FORMAT);
        }

        return results;
    }

    public static ServerConfig getServerConfig(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(filePath), ServerConfig.class);
    }

    public static ClientConfig getClientConfig(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(filePath), ClientConfig.class);
    }
}
