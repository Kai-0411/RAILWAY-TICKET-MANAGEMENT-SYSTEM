package com.railway.service;

import com.railway.model.*;
import com.railway.pricing.PricingStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
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

    public void  registerTrain(Train train) {
        trainRepository.put(train.getTrainNumber(), train);
    }

    public double calculateFare(Station source, Station destination, SeatClass seatClass) {
        return pricingStrategy.calculateFare(source, destination, seatClass);
    }

    public Ticket bookTicket(String trainNumber, Station source, Station destination,
                             List<Passenger> passengers, SeatClass seatClass) {
        return bookTicket(trainNumber, source, destination, passengers, seatClass, LocalDate.now());
    }

    public Ticket bookTicket(String trainNumber, Station source, Station destination,
                             List<Passenger> passengers, SeatClass seatClass, LocalDate journeyDate) {
        Objects.requireNonNull(journeyDate, "Journey date cannot be null.");
        Train train = trainRepository.get(trainNumber);
        if (train == null) {
            throw new NoSuchElementException("Train not found with ID: " + trainNumber);
        }
        if (!train.getDepartureDateTime(journeyDate).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot book a train that has already departed.");
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
                    destination,
                    journeyDate
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
                    .forEach(seat -> seat.release(journeyDate, fromSegment, toSegment));
                allocations.clear();

                Map<Passenger, Seat> waitlistAssignments = new HashMap<>();
                // Queue passengers on waitlist
                for (Passenger passenger : passengers) {
                    boolean waitlisted = train.enqueueWaitlist(
                        new PassengerBookingContext(passenger, seatClass, source, destination, journeyDate)
                    );
                    if (!waitlisted) {
                        throw new IllegalStateException("Train full. Waitlist capacity exceeded.");
                    }
                    waitlistAssignments.put(passenger, null);
                }

                Ticket waitlistTicket = new Ticket(
                    trainNumber,
                    journeyDate,
                    train.getDepartureDateTime(journeyDate),
                    source,
                    destination,
                    waitlistAssignments,
                    0.0,
                    BookingStatus.WAITLISTED,
                    seatClass
                );
                ticketRepository.put(waitlistTicket.getPnr(), waitlistTicket);
                for (Passenger passenger : passengers) {
                    waitlistTicketByPassengerId.put(passenger.id(), waitlistTicket.getPnr());
                }
                return waitlistTicket;
            }

            Ticket confirmedTicket = new Ticket(
                trainNumber, journeyDate, train.getDepartureDateTime(journeyDate), source, destination,
                allocations, totalFare, BookingStatus.CONFIRMED, seatClass
            );
            ticketRepository.put(confirmedTicket.getPnr(), confirmedTicket);
            return confirmedTicket;

        } finally {
            globalTransactionLock.unlock();
        }
    }

    public CancellationResult previewCancellation(String pnr) {
        return previewCancellation(pnr, LocalDateTime.now());
    }

    public CancellationResult previewCancellation(String pnr, LocalDateTime cancellationTime) {
        Objects.requireNonNull(cancellationTime, "Cancellation time cannot be null.");
        globalTransactionLock.lock();
        try {
            return calculateCancellationResult(requireActiveTicket(pnr), cancellationTime);
        } finally {
            globalTransactionLock.unlock();
        }
    }

    public CancellationResult cancelTicket(String pnr) {
        return cancelTicket(pnr, LocalDateTime.now());
    }

    public CancellationResult cancelTicket(String pnr, LocalDateTime cancellationTime) {
        Objects.requireNonNull(cancellationTime, "Cancellation time cannot be null.");
        globalTransactionLock.lock();
        try {
            Ticket ticket = requireActiveTicket(pnr);
            CancellationResult cancellation = calculateCancellationResult(ticket, cancellationTime);

            Train train = trainRepository.get(ticket.getTrainNumber());
            int fromSegment = train.getRoute().indexOf(ticket.getSource());
            int toSegment = train.getRoute().indexOf(ticket.getDestination());
            if (ticket.getStatus() == BookingStatus.WAITLISTED) {
                for (Passenger passenger : ticket.getBookedSeats().keySet()) {
                    train.removeWaitlistedPassenger(passenger.id());
                    waitlistTicketByPassengerId.remove(passenger.id(), pnr);
                }
            }
            ticket.cancel(
                ticket.getJourneyDate(),
                fromSegment,
                toSegment,
                cancellation.cancellationCharge(),
                cancellation.refundAmount()
            );

            int queuedPassengers = train.getWaitlistSize();
            for (int i = 0; i < queuedPassengers; i++) {
                Optional<PassengerBookingContext> nextInLine = train.pollWaitlist();
                if (nextInLine.isEmpty()) {
                    break;
                }

                PassengerBookingContext context = nextInLine.get();
                if (!context.journeyDate().equals(ticket.getJourneyDate())) {
                    train.enqueueWaitlist(context);
                    continue;
                }
                Optional<Seat> reclaimedSeat = train.bookSeat(
                    context.seatClass(),
                    context.passenger().preference(),
                    context.source(),
                    context.destination(),
                    context.journeyDate()
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
                            waitlistTicket.getJourneyDate(),
                            waitlistTicket.getDepartureDateTime(),
                            waitlistTicket.getSource(),
                            waitlistTicket.getDestination(),
                            updatedAllocation,
                            pricingStrategy.calculateFare(
                                waitlistTicket.getSource(),
                                waitlistTicket.getDestination(),
                                context.seatClass()
                            ),
                            BookingStatus.CONFIRMED,
                            context.seatClass()
                        );
                        ticketRepository.put(waitlistTicketPnr, promotedTicket);
                        System.out.println("Passenger " + context.passenger().fullName() +
                                           " promoted from WAITLIST to CONFIRMED! PNR: " + promotedTicket.getPnr() +
                                           ", Seat: " + reclaimedSeat.get().getSeatNumber());
                    } else {
                        updatedAllocation.put(context.passenger(), reclaimedSeat.get());
                        Ticket promotedTicket = new Ticket(
                            train.getTrainNumber(),
                            context.journeyDate(),
                            train.getDepartureDateTime(context.journeyDate()),
                            context.source(),
                            context.destination(),
                            updatedAllocation,
                            pricingStrategy.calculateFare(
                                context.source(),
                                context.destination(),
                                context.seatClass()
                            ),
                            BookingStatus.CONFIRMED,
                            context.seatClass()
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
            return cancellation;
        } finally {
            globalTransactionLock.unlock();
        }
    }

    private Ticket requireActiveTicket(String pnr) {
        Ticket ticket = ticketRepository.get(pnr);
        if (ticket == null || ticket.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Ticket invalid or already cancelled.");
        }
        return ticket;
    }

    private CancellationResult calculateCancellationResult(Ticket ticket, LocalDateTime cancellationTime) {
        LocalDateTime departure = ticket.getDepartureDateTime();
        Duration timeUntilDeparture = Duration.between(cancellationTime, departure);
        if (timeUntilDeparture.isNegative() || timeUntilDeparture.isZero()) {
            throw new IllegalArgumentException("Ticket cannot be cancelled after the train has departed.");
        }

        double chargePercentage;
        if (timeUntilDeparture.compareTo(Duration.ofHours(48)) >= 0) {
            chargePercentage = 10.0;
        } else if (timeUntilDeparture.compareTo(Duration.ofHours(24)) >= 0) {
            chargePercentage = 25.0;
        } else {
            chargePercentage = 100.0;
        }

        double cancellationCharge = BigDecimal.valueOf(ticket.getTotalFare())
            .multiply(BigDecimal.valueOf(chargePercentage))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .doubleValue();
        double refundAmount = BigDecimal.valueOf(ticket.getTotalFare())
            .subtract(BigDecimal.valueOf(cancellationCharge))
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
        return new CancellationResult(
            ticket.getPnr(),
            departure,
            timeUntilDeparture,
            ticket.getTotalFare(),
            chargePercentage,
            cancellationCharge,
            refundAmount
        );
    }

    public Optional<Ticket> getTicket(String pnr) {
        return Optional.ofNullable(ticketRepository.get(pnr));
    }
}