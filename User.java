import java.io.Serializable;
import java.util.List;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    public String username;
    private String password;
    public UserLevel level;
    public int numberOfReviews;
    public boolean isLogged = false; //false di default
    public List<String> citiesForNotifications;

    public User() {
        //default constructor
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserLevel getLevel() {
        return level;
    }
    public void setLevel(UserLevel level) {
        this.level = level;
    }
    public boolean getIsLogged() {
        return isLogged;
    }

    public void setIsLogged(boolean isLogged) {this.isLogged = isLogged;
    }

    public List<String> getCitiesForNotifications() {
        return citiesForNotifications;
    }

    public void setCitiesForNotifications(List<String> citiesForNotifications) {
        this.citiesForNotifications = citiesForNotifications;
    }

    public int getNumberOfReviews() {
        return numberOfReviews;
    }

    public void setNumberOfReviews(int numberOfReviews) {
        this.numberOfReviews = numberOfReviews;
    }
}
