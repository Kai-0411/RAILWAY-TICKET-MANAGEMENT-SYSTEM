package com.railway.service;

import com.railway.model.*;
import com.railway.pricing.PricingStrategy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BookingService {
    private final Map<String, Train> trainRepository = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketRepository = new ConcurrentHashMap<>();
    private final Map<String, String> waitlistTicketByPassengerId = new ConcurrentHashMap<>();
    private final PricingStrategy pricingStrategy;
    private final ReentrantLock globalTransactionLock = new ReentrantLock();

    public BookingService(PricingStrategy pricingStrategy) {
        this.pricingStrategy = pricingStrategy;
    }

    public void registerTrain(Train train) {
        trainRepository.put(train.getTrainNumber(), train);
    }

    public Ticket bookTicket(String trainNumber, Station source, Station destination,
                             List<Passenger> passengers, SeatClass seatClass) {
        Train train = trainRepository.get(trainNumber);
        if (train == null) {
            throw new NoSuchElementException("Train not found with ID: " + trainNumber);
        }
        if (!train.getRoute().contains(source) || !train.getRoute().contains(destination)
                || train.getRoute().indexOf(destination) <= train.getRoute().indexOf(source)) {
            throw new IllegalArgumentException("Journey stations must follow this train's route.");
        }

        Map<Passenger, Seat> allocations = new HashMap<>();
        double totalFare = 0.0;
        double farePerSeat = pricingStrategy.calculateFare(source, destination, seatClass);

        globalTransactionLock.lock();
        try {
            boolean allAllocated = true;
            for (Passenger passenger : passengers) {
                Optional<Seat> allocatedSeat = train.bookSeat(
                    seatClass,
                    passenger.preference(),
                    source,
                    destination
                );
                if (allocatedSeat.isPresent()) {
                    allocations.put(passenger, allocatedSeat.get());
                    totalFare += farePerSeat;
                } else {
                    allAllocated = false;
                    break;
                }
            }

            if (!allAllocated) {
                // Rollback any seats reserved in this batch
                int fromSegment = train.getRoute().indexOf(source);
                int toSegment = train.getRoute().indexOf(destination);
                allocations.values().stream()
                    .filter(Objects::nonNull)
                    .forEach(seat -> seat.release(fromSegment, toSegment));
                allocations.clear();

                Map<Passenger, Seat> waitlistAssignments = new HashMap<>();
                // Queue passengers on waitlist
                for (Passenger passenger : passengers) {
                    boolean waitlisted = train.enqueueWaitlist(
                        new PassengerBookingContext(passenger, seatClass, source, destination)
                    );
                    if (!waitlisted) {
                        throw new IllegalStateException("Train full. Waitlist capacity exceeded.");
                    }
                    waitlistAssignments.put(passenger, null);
                }

                Ticket waitlistTicket = new Ticket(trainNumber, source, destination, waitlistAssignments, 0.0, BookingStatus.WAITLISTED);
                ticketRepository.put(waitlistTicket.getPnr(), waitlistTicket);
                for (Passenger passenger : passengers) {
                    waitlistTicketByPassengerId.put(passenger.id(), waitlistTicket.getPnr());
                }
                return waitlistTicket;
            }

            Ticket confirmedTicket = new Ticket(trainNumber, source, destination, allocations, totalFare, BookingStatus.CONFIRMED);
            ticketRepository.put(confirmedTicket.getPnr(), confirmedTicket);
            return confirmedTicket;

        } finally {
            globalTransactionLock.unlock();
        }
    }

    public void cancelTicket(String pnr) {
        globalTransactionLock.lock();
        try {
            Ticket ticket = ticketRepository.get(pnr);
            if (ticket == null || ticket.getStatus() == BookingStatus.CANCELLED) {
                throw new IllegalArgumentException("Ticket invalid or already cancelled.");
            }

            Train train = trainRepository.get(ticket.getTrainNumber());
            int fromSegment = train.getRoute().indexOf(ticket.getSource());
            int toSegment = train.getRoute().indexOf(ticket.getDestination());
            ticket.cancel(fromSegment, toSegment);

            // Check if freed inventory can satisfy waiting passengers
            int waitingPassengers = train.getWaitlistSize();
            for (int i = 0; i < waitingPassengers; i++) {
                Optional<PassengerBookingContext> nextInLine = train.pollWaitlist();
                if (nextInLine.isEmpty()) {
                    break;
                }

                PassengerBookingContext context = nextInLine.get();
                Optional<Seat> reclaimedSeat = train.bookSeat(
                    context.seatClass(),
                    context.passenger().preference(),
                    context.source(),
                    context.destination()
                );

                if (reclaimedSeat.isPresent()) {
                    String waitlistTicketPnr = waitlistTicketByPassengerId.remove(context.passenger().id());
                    Ticket waitlistTicket = waitlistTicketPnr == null ? null : ticketRepository.get(waitlistTicketPnr);
                    Map<Passenger, Seat> updatedAllocation = new HashMap<>();

                    if (waitlistTicket != null && waitlistTicket.getStatus() == BookingStatus.WAITLISTED) {
                        updatedAllocation.putAll(waitlistTicket.getBookedSeats());
                        updatedAllocation.put(context.passenger(), reclaimedSeat.get());
                        Ticket promotedTicket = new Ticket(
                            waitlistTicketPnr,
                            train.getTrainNumber(),
                            waitlistTicket.getSource(),
                            waitlistTicket.getDestination(),
                            updatedAllocation,
                            pricingStrategy.calculateFare(
                                waitlistTicket.getSource(),
                                waitlistTicket.getDestination(),
                                context.seatClass()
                            ),
                            BookingStatus.CONFIRMED
                        );
                        ticketRepository.put(waitlistTicketPnr, promotedTicket);
                        System.out.println("Passenger " + context.passenger().fullName() +
                                           " promoted from WAITLIST to CONFIRMED! PNR: " + promotedTicket.getPnr() +
                                           ", Seat: " + reclaimedSeat.get().getSeatNumber());
                    } else {
                        updatedAllocation.put(context.passenger(), reclaimedSeat.get());
                        Ticket promotedTicket = new Ticket(
                            train.getTrainNumber(),
                            context.source(),
                            context.destination(),
                            updatedAllocation,
                            pricingStrategy.calculateFare(
                                context.source(),
                                context.destination(),
                                context.seatClass()
                            ),
                            BookingStatus.CONFIRMED
                        );
                        ticketRepository.put(promotedTicket.getPnr(), promotedTicket);
                        System.out.println("Passenger " + context.passenger().fullName() +
                                           " promoted from WAITLIST to CONFIRMED! PNR: " + promotedTicket.getPnr() +
                                           ", Seat: " + reclaimedSeat.get().getSeatNumber());
                    }
                } else {
                    train.enqueueWaitlist(context);
                }
            }
        } finally {
            globalTransactionLock.unlock();
        }
    }

    public Optional<Ticket> getTicket(String pnr) {
        return Optional.ofNullable(ticketRepository.get(pnr));
    }
}