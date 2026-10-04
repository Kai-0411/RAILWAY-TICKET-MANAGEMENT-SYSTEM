package com.railway.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class Ticket {
    private final String pnr;
    private final String trainNumber;
    private final Station source;
    private final Station destination;
    private final Map<Passenger, Seat> bookedSeats;
    private final double totalFare;
    private BookingStatus status;
    private final LocalDateTime bookingTimestamp;

    public Ticket(String trainNumber, Station source, Station destination,
                  Map<Passenger, Seat> bookedSeats, double totalFare, BookingStatus status) {
        this("PNR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                trainNumber, source, destination, bookedSeats, totalFare, status);
    }

    public Ticket(String pnr, String trainNumber, Station source, Station destination,
                  Map<Passenger, Seat> bookedSeats, double totalFare, BookingStatus status) {
        this.pnr = pnr;
        this.trainNumber = trainNumber;
        this.source = source;
        this.destination = destination;
        this.bookedSeats = bookedSeats;
        this.totalFare = totalFare;
        this.status = status;
        this.bookingTimestamp = LocalDateTime.now();
    }

    public void cancel(int fromSegment, int toSegment) {
        this.status = BookingStatus.CANCELLED;
        for (Seat seat : bookedSeats.values()) {
            if (seat != null) {
                seat.release(fromSegment, toSegment);
            }
        }
    }

    public String getPnr() {
        return pnr;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public Station getSource() {
        return source;
    }

    public Station getDestination() {
        return destination;
    }

    public Map<Passenger, Seat> getBookedSeats() {
        return Collections.unmodifiableMap(bookedSeats);
    }

    public double getTotalFare() {
        return totalFare;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getBookingTimestamp() {
        return bookingTimestamp;
    }
}
