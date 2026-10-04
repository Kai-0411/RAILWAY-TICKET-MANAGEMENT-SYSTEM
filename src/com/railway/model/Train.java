package com.railway.model;

import java.util.*;

public class Train {
    private final String trainNumber;
    private final String trainName;
    private final List<Station> route;
    private final List<Coach> coaches;
    private final Queue<PassengerBookingContext> waitlist;
    private final int maxWaitlistCapacity;

    public Train(String trainNumber, String trainName, List<Station> route, int maxWaitlistCapacity) {
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.route = new ArrayList<>(route);
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
        int fromSegment = route.indexOf(source);
        int toSegment = route.indexOf(destination);
        if (fromSegment < 0 || toSegment <= fromSegment) {
            throw new IllegalArgumentException("Journey stations must follow this train's route.");
        }
        return coaches.stream()
                .filter(c -> c.getSeatClass() == seatClass)
                .map(c -> c.allocateSeat(preference, fromSegment, toSegment))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public long getAvailableSeatCount(SeatClass seatClass, Station source, Station destination) {
        int fromSegment = route.indexOf(source);
        int toSegment = route.indexOf(destination);
        if (fromSegment < 0 || toSegment <= fromSegment) {
            throw new IllegalArgumentException("Journey stations must follow this train's route.");
        }
        return coaches.stream()
            .filter(coach -> coach.getSeatClass() == seatClass)
            .mapToLong(coach -> coach.getAvailableSeatCount(fromSegment, toSegment))
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

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public List<Station> getRoute() {
        return Collections.unmodifiableList(route);
    }

    public int getWaitlistSize() {
        return waitlist.size();
    }
}
