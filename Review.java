import java.io.Serializable;
import java.time.LocalDateTime;

public class Review implements Serializable {
    private static final long serialVersionUID = 1L;
    public Hotel hotel;
    public Rating ratings;
    public double overall;
    public LocalDateTime dateOfCreation;
    public double reviewWeight; //parametro per assegnare il "peso" della recensione

    public Review(){
        //default constructor
    }


    public Rating getRatings() {
        return ratings;
    }
    public void setRatings(Rating ratings) {
        this.ratings = ratings;
    }
    public double getOverall() {
        return overall;
    }
    public void setOverall(double overall) {
        this.overall = overall;
    }
    public LocalDateTime getDateOfCreation() {
        return dateOfCreation;
    }
    public void setDateOfCreation(LocalDateTime dateOfCreation) {
        this.dateOfCreation = dateOfCreation;
    }
    public Hotel getHotel() {
        return hotel;
    }
    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }
    public double getReviewWeight() {
        return reviewWeight;
    }
    public void setReviewWeight(double reviewWeight) {
        this.reviewWeight = reviewWeight;
    }
}
