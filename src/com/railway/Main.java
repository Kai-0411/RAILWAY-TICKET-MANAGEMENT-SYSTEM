package com.railway;

import com.railway.model.*;
import com.railway.pricing.StandardDistancePricingStrategy;
import com.railway.service.BookingService;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final SeatClass AVAILABLE_CLASS = SeatClass.THIRD_AC;
    private static final List<Station> STATIONS = List.of(
        new Station("NDLS", "New Delhi", 0),
        new Station("CNB", "Kanpur Central", 440),
        new Station("HWH", "Howrah Junction", 1450)
    );
    private static final List<Train> AVAILABLE_TRAINS = createSampleTrains();

    public static void main(String[] args) {
        BookingService bookingService = createBookingService(AVAILABLE_TRAINS);

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Railway Ticket Booking");
            System.out.println("Available trains:");
            AVAILABLE_TRAINS.forEach(Main::printTrain);

            boolean running = true;
            while (running) {
                System.out.println("\n1. Book ticket");
                System.out.println("2. Cancel ticket");
                System.out.println("3. Check ticket");
                System.out.println("4. Exit");

                switch (readInt(scanner, "Choose an option: ", 1, 4)) {
                    case 1 -> bookTicket(scanner, bookingService);
                    case 2 -> cancelTicket(scanner, bookingService);
                    case 3 -> checkTicket(scanner, bookingService);
                    case 4 -> running = false;
                    default -> throw new IllegalStateException("Unexpected menu selection.");
                }
            }
        }
        System.out.println("Goodbye.");
    }

    static List<Train> createSampleTrains() {
        return List.of(
            createTrain("12302", "Howrah Rajdhani Express", STATIONS, LocalTime.of(16, 55)),
            createTrain("12304", "Howrah Mail", STATIONS, LocalTime.of(19, 30)),
            createTrain("12301", "New Delhi Rajdhani Express", reversedRoute(), LocalTime.of(17, 10)),
            createTrain("12303", "New Delhi Mail", reversedRoute(), LocalTime.of(21, 0))
        );
    }

    static BookingService createBookingService(List<Train> trains) {
        BookingService bookingService = new BookingService(new StandardDistancePricingStrategy());
        trains.forEach(bookingService::registerTrain);
        return bookingService;
    }

    private static void bookTicket(Scanner scanner, BookingService bookingService) {
        Train train = readTrain(scanner);
        String name = readNonBlank(scanner, "Passenger name: ");
        int age = readInt(scanner, "Passenger age: ", 0, 120);
        BerthPreference preference = readChoice(
            scanner,
            "Berth preference:",
            BerthPreference.values()
        );
        Station source = readStation(scanner, "Source station:", train.getRoute());
        while (train.getRoute().indexOf(source) == train.getRoute().size() - 1) {
            System.out.println("There are no later stations after this source on the selected train.");
            source = readStation(scanner, "Choose a different source station:", train.getRoute());
        }
        Station destination = readStation(scanner, "Destination station:", train.getRoute());
        while (train.getRoute().indexOf(destination) <= train.getRoute().indexOf(source)) {
            System.out.println("Destination must be after the source on this train's route.");
            destination = readStation(scanner, "Destination station:", train.getRoute());
        }

        Passenger passenger =  new Passenger(
            UUID.randomUUID().toString(),
            name,
            age,
            preference
        );

        try {
            Ticket ticket = bookingService.bookTicket(
                train.getTrainNumber(),
                source,
                destination,
                List.of(passenger),
                AVAILABLE_CLASS
            );
            System.out.println("Booking " + ticket.getStatus());
            System.out.println("PNR: " + ticket.getPnr());
            System.out.printf(Locale.ROOT, "Fare: INR %.2f%n", ticket.getTotalFare());
            long availableSeats = train.getAvailableSeatCount(AVAILABLE_CLASS, source, destination);
            System.out.println("Available seats for this journey on " + train.getTrainNumber() +
                ": " + availableSeats + " of 3");
        } catch (IllegalStateException e) {
            System.out.println("Booking failed: " + e.getMessage());
        }
    }

    private static void cancelTicket(Scanner scanner, BookingService bookingService) {
        String pnr = readNonBlank(scanner, "Enter PNR to cancel: ");
        try {
            CancellationResult quote = bookingService.previewCancellation(pnr);
            System.out.printf(
                Locale.ROOT,
                "Departure: %s | Fare: INR %.2f | Cancellation charge: INR %.2f (%.0f%%) | Refund: INR %.2f%n",
                quote.departureDateTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")),
                quote.originalFare(),
                quote.cancellationCharge(),
                quote.chargePercentage(),
                quote.refundAmount()
            );
            if (readInt(scanner, "Confirm cancellation? 1. Yes  2. No: ", 1, 2) != 1) {
                System.out.println("Cancellation not made.");
                return;
            }
            CancellationResult result = bookingService.cancelTicket(pnr);
            System.out.printf(
                Locale.ROOT,
                "Ticket %s cancelled. Charge: INR %.2f (%.0f%%). Refund: INR %.2f%n",
                pnr,
                result.cancellationCharge(),
                result.chargePercentage(),
                result.refundAmount()
            );
        } catch (IllegalArgumentException e) {
            System.out.println("Cancellation failed: " + e.getMessage());
        }
    }

    private static void checkTicket(Scanner scanner, BookingService bookingService) {
        String pnr = readNonBlank(scanner, "Enter PNR: ");
        bookingService.getTicket(pnr).ifPresentOrElse(
            ticket -> {
                System.out.println("PNR: " + ticket.getPnr());
                System.out.println("Train: " + ticket.getTrainNumber());
                System.out.println("Route: " + ticket.getSource().name() + " to " +
                    ticket.getDestination().name());
                System.out.println("Status: " + ticket.getStatus());
                System.out.printf(Locale.ROOT, "Fare: INR %.2f%n", ticket.getTotalFare());
                ticket.getBookedSeats().forEach((passenger, seat) -> {
                    System.out.println("Passenger: " + passenger.fullName());
                    if (seat != null) {
                        System.out.println("Seat: " + seat.getSeatNumber());
                    }
                });
            },
            () -> System.out.println("No ticket found for PNR " + pnr + ".")
        );
    }

    private static Train readTrain(Scanner scanner) {
        System.out.println("Choose a train:");
        for (int i = 0; i < AVAILABLE_TRAINS.size(); i++) {
            System.out.print((i + 1) + ". ");
            printTrain(AVAILABLE_TRAINS.get(i));
        }
        int choice = readInt(scanner, "Choose a train: ", 1, AVAILABLE_TRAINS.size());
        return AVAILABLE_TRAINS.get(choice - 1);
    }

    private static void printTrain(Train train) {
        String route = train.getRoute().stream()
            .map(Station::name)
            .reduce((first, second) -> first + " -> " + second)
            .orElse("");
        System.out.println(train.getTrainNumber() + " - " + train.getTrainName() +
            " | " + route + " | Departs " + train.getDepartureTime() +
            " | " + AVAILABLE_CLASS + " | 3 seats");
    }

    private static Station readStation(Scanner scanner, String prompt, List<Station> route) {
        System.out.println(prompt);
        for (int i = 0; i < route.size(); i++) {
            Station station = route.get(i);
            System.out.println((i + 1) + ". " + station.name() + " (" + station.code() + ")");
        }
        int choice = readInt(scanner, "Choose a station: ", 1, route.size());
        return route.get(choice - 1);
    }

    private static Train createTrain(
        String trainNumber,
        String trainName,
        List<Station> route,
        LocalTime departureTime
    ) {
        Train train = new Train(trainNumber, trainName, route, 20, departureTime);
        for (SeatClass seatClass : SeatClass.values()) {
            train.addCoach(new Coach("B" + (seatClass.ordinal() + 1), seatClass, 3));
        }
        return train;
    }

    private static List<Station> reversedRoute() {
        return List.of(STATIONS.get(2), STATIONS.get(1), STATIONS.get(0));
    }

    private static <T extends Enum<T>> T readChoice(
        Scanner scanner,
        String prompt,
        T[] choices
    ) {
        System.out.println(prompt);
        for (int i = 0; i < choices.length; i++) {
            System.out.println((i + 1) + ". " + choices[i]);
        }
        int choice = readInt(scanner, "Choose an option: ", 1, choices.length);
        return choices[choice - 1];
    }

    private static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Invalid input is reported below and requested again.
            }
            System.out.println("Enter a whole number from " + min + " to " + max + ".");
        }
    }

    private static String readNonBlank(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("This value cannot be blank.");
        }
    }
}
