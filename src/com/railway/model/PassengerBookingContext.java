package com.railway.model;

import java.time.LocalDate;

public record PassengerBookingContext(
    Passenger passenger,
    SeatClass seatClass,
    Station source,
    Station destination,
    LocalDate journeyDate
) {
    public PassengerBookingContext(
        Passenger passenger,
        SeatClass seatClass,
        Station source,
        Station destination
    ) {
        this(passenger, seatClass, source, destination, LocalDate.now());
    }
}