package com.railway.model;

import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Train {
    private final String trainNumber;
    private final String trainName;
    private final List<Station> route;
    private final LocalTime departureTime;
    private final List<Coach> coaches;
    private final Queue<PassengerBookingContext> waitlist;
    private final int maxWaitlistCapacity;

    public Train(String trainNumber, String trainName, List<Station> route, int maxWaitlistCapacity) {
        this(trainNumber, trainName, route, maxWaitlistCapacity, LocalTime.MIDNIGHT);
    }

    public Train(
        String trainNumber,
        String trainName,
        List<Station> route,
        int maxWaitlistCapacity,
        LocalTime departureTime
    ) {
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.route = new ArrayList<>(route);
        this.departureTime = Objects.requireNonNull(departureTime, "Departure time cannot be null.");
        this.coaches = new ArrayList<>();
        this.waitlist = new LinkedList<>();
        this.maxWaitlistCapacity = maxWaitlistCapacity;
    }

    public synchronized void addCoach(Coach coach) {
        this.coaches.add(coach);
    }

    public Optional<Seat> bookSeat(
        SeatClass seatClass,
        BerthPreference preference,
        Station source,
        Station destination
    ) {
        return bookSeat(seatClass, preference, source, destination, LocalDate.now());
    }

    public Optional<Seat> bookSeat(
        SeatClass seatClass,
        BerthPreference preference,
        Station source,
        Station destination,
        LocalDate journeyDate
    ) {
        int fromSegment = route.indexOf(source);
        int toSegment = route.indexOf(destination);
        if (fromSegment < 0 || toSegment <= fromSegment) {
            throw new IllegalArgumentException("Journey stations must follow this train's route.");
        }
        return coaches.stream()
                .filter(c -> c.getSeatClass() == seatClass)
                .map(c -> c.allocateSeat(preference, journeyDate, fromSegment, toSegment))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public long getAvailableSeatCount(SeatClass seatClass, Station source, Station destination) {
        return getAvailableSeatCount(seatClass, source, destination, LocalDate.now());
    }

    public long getAvailableSeatCount(
        SeatClass seatClass,
        Station source,
        Station destination,
        LocalDate journeyDate
    ) {
        int fromSegment = route.indexOf(source);
        int toSegment = route.indexOf(destination);
        if (fromSegment < 0 || toSegment <= fromSegment) {
            throw new IllegalArgumentException("Journey stations must follow this train's route.");
        }
        return coaches.stream()
            .filter(coach -> coach.getSeatClass() == seatClass)
            .mapToLong(coach -> coach.getAvailableSeatCount(journeyDate, fromSegment, toSegment))
            .sum();
    }

    public synchronized boolean enqueueWaitlist(PassengerBookingContext context) {
        if (waitlist.size() < maxWaitlistCapacity) {
            return waitlist.offer(context);
        }
        return false;
    }

    public synchronized Optional<PassengerBookingContext> pollWaitlist() {
        return Optional.ofNullable(waitlist.poll());
    }

    public synchronized boolean removeWaitlistedPassenger(String passengerId) {
        return waitlist.removeIf(context -> context.passenger().id().equals(passengerId));
    }

    public synchronized int getWaitlistSize(LocalDate journeyDate) {
        return (int) waitlist.stream()
            .filter(context -> context.journeyDate().equals(journeyDate))
            .count();
    }

    public synchronized int getWaitlistSize(LocalDate journeyDate, SeatClass seatClass) {
        return (int) waitlist.stream()
            .filter(context -> context.journeyDate().equals(journeyDate))
            .filter(context -> context.seatClass() == seatClass)
            .count();
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public LocalDateTime getDepartureDateTime(LocalDate journeyDate) {
        return journeyDate.atTime(departureTime);
    }

    public List<Station> getRoute() {
        return Collections.unmodifiableList(route);
    }

    public int getWaitlistSize() {
        return waitlist.size();
    }
}
