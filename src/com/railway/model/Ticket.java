package com.railway.model;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

public class Ticket {
    private static final SecureRandom PNR_RANDOM = new SecureRandom();
    private final String pnr;
    private final String trainNumber;
    private final LocalDate journeyDate;
    private final LocalDateTime departureDateTime;
    private final SeatClass seatClass;
    private final Station source;
    private final Station destination;
    private final Map<Passenger, Seat> bookedSeats;
    private final double totalFare;
    private double cancellationCharge;
    private double refundAmount;
    private BookingStatus status;
    private final LocalDateTime bookingTimestamp;

    public Ticket(String trainNumber, Station source, Station destination,
                  Map<Passenger, Seat> bookedSeats, double totalFare, BookingStatus status) {
        this(generatePnr(), trainNumber, LocalDate.now(), source, destination,
            bookedSeats, totalFare, status, SeatClass.THIRD_AC);
    }

    public Ticket(String pnr, String trainNumber, Station source, Station destination,
                  Map<Passenger, Seat> bookedSeats, double totalFare, BookingStatus status) {
        this(pnr, trainNumber, LocalDate.now(), source, destination,
            bookedSeats, totalFare, status, SeatClass.THIRD_AC);
    }

    public Ticket(String trainNumber, LocalDate journeyDate, Station source, Station destination,
                  Map<Passenger, Seat> bookedSeats, double totalFare, BookingStatus status) {
        this(generatePnr(), trainNumber, journeyDate, source, destination,
            bookedSeats, totalFare, status, SeatClass.THIRD_AC);
    }

    public Ticket(String trainNumber, LocalDate journeyDate, Station source, Station destination,
                  Map<Passenger, Seat> bookedSeats, double totalFare, BookingStatus status,
                  SeatClass seatClass) {
        this(generatePnr(), trainNumber, journeyDate, source, destination,
            bookedSeats, totalFare, status, seatClass);
    }

    public Ticket(
        String trainNumber,
        LocalDate journeyDate,
        LocalDateTime departureDateTime,
        Station source,
        Station destination,
        Map<Passenger, Seat> bookedSeats,
        double totalFare,
        BookingStatus status,
        SeatClass seatClass
    ) {
        this(generatePnr(), trainNumber, journeyDate, departureDateTime,
            source, destination, bookedSeats, totalFare, status, seatClass);
    }

    public Ticket(String pnr, String trainNumber, LocalDate journeyDate, Station source,
                  Station destination, Map<Passenger, Seat> bookedSeats, double totalFare,
                  BookingStatus status) {
        this(pnr, trainNumber, journeyDate, source, destination,
            bookedSeats, totalFare, status, SeatClass.THIRD_AC);
    }

    public Ticket(String pnr, String trainNumber, LocalDate journeyDate, Station source,
                  Station destination, Map<Passenger, Seat> bookedSeats, double totalFare,
                  BookingStatus status, SeatClass seatClass) {
        this(pnr, trainNumber, journeyDate, journeyDate.atStartOfDay(),
            source, destination, bookedSeats, totalFare, status, seatClass);
    }

    public Ticket(
        String pnr,
        String trainNumber,
        LocalDate journeyDate,
        LocalDateTime departureDateTime,
        Station source,
        Station destination,
        Map<Passenger, Seat> bookedSeats,
        double totalFare,
        BookingStatus status,
        SeatClass seatClass
    ) {
        if (pnr == null || !pnr.matches("\\d+")) {
            throw new IllegalArgumentException("PNR must contain digits only.");
        }
        this.pnr = pnr;
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        this.departureDateTime = departureDateTime;
        this.seatClass = seatClass;
        this.source = source;
        this.destination = destination;
        this.bookedSeats = bookedSeats;
        this.totalFare = totalFare;
        this.status = status;
        this.bookingTimestamp = LocalDateTime.now();
    }

    public void cancel(int fromSegment, int toSegment) {
        cancel(journeyDate, fromSegment, toSegment);
    }

    public void cancel(LocalDate journeyDate, int fromSegment, int toSegment) {
        cancel(journeyDate, fromSegment, toSegment, 0.0, 0.0);
    }

    public void cancel(
        LocalDate journeyDate,
        int fromSegment,
        int toSegment,
        double cancellationCharge,
        double refundAmount
    ) {
        this.status = BookingStatus.CANCELLED;
        this.cancellationCharge = cancellationCharge;
        this.refundAmount = refundAmount;
        for (Seat seat : bookedSeats.values()) {
            if (seat != null) {
                seat.release(journeyDate, fromSegment, toSegment);
            }
        }
    }

    public String getPnr() {
        return pnr;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public LocalDate getJourneyDate() {
        return journeyDate;
    }

    public LocalDateTime getDepartureDateTime() {
        return departureDateTime;
    }

    public SeatClass getSeatClass() {
        return seatClass;
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

    public double getCancellationCharge() {
        return cancellationCharge;
    }

    public double getRefundAmount() {
        return refundAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getBookingTimestamp() {
        return bookingTimestamp;
    }

    private static String generatePnr() {
        return Long.toString(1_000_000_000L + PNR_RANDOM.nextLong(9_000_000_000L));
    }
}
