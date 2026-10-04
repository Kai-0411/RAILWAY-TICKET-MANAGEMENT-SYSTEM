package com.railway.model;

public record PassengerBookingContext(
    Passenger passenger,
    SeatClass seatClass,
    Station source,
    Station destination
) {

}