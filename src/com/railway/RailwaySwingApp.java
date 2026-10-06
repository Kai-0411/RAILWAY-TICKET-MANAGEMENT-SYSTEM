package com.railway;

import com.railway.model.BerthPreference;
import com.railway.model.BookingStatus;
import com.railway.model.CancellationResult;
import com.railway.model.Passenger;
import com.railway.model.SeatClass;
import com.railway.model.Station;
import com.railway.model.Ticket;
import com.railway.model.Train;
import com.railway.service.BookingService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RailwaySwingApp extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color NAVY = new Color(20, 42, 75);
    private static final Color BLUE = new Color(37, 99, 173);
    private static final Color BACKGROUND = new Color(244, 247, 251);
    private static final Color MUTED = new Color(101, 116, 139);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DEPARTURE_FORMAT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final String SEARCH_CARD = "search";
    private static final String TRAINS_CARD = "trains";
    private static final String PASSENGER_CARD = "passenger";
    private static final String REVIEW_CARD = "review";
    private static final String PAYMENT_CARD = "payment";

    private final transient List<Train> trains;
    private final transient List<Station> stations;
    private final transient BookingService bookingService;
    private final CardLayout bookingLayout = new CardLayout();
    private final JPanel bookingCards = new JPanel(bookingLayout);
    private final JComboBox<Station> sourcePicker = new JComboBox<>();
    private final JComboBox<Station> destinationPicker = new JComboBox<>();
    private final JComboBox<SeatClass> seatClassPicker = new JComboBox<>(SeatClass.values());
    private final JSpinner journeyDatePicker;
    private final JComboBox<TrainOption> trainPicker = new JComboBox<>();
    private final JTextField passengerName = new JTextField(22);
    private final JTextField passengerAge = new JTextField(22);
    private final JComboBox<BerthPreference> berthPicker =
        new JComboBox<>(BerthPreference.values());
    private final JLabel reviewDetails = new JLabel();
    private final JLabel paymentDetails = new JLabel();
    private final JLabel classAvailabilityLabel = new JLabel();
    private final JTextField pnrField = new JTextField(18);
    private final JTextArea ticketDetails = new JTextArea(10, 35);
    private transient Train selectedTrain;
    private transient SeatClass selectedSeatClass;

    private RailwaySwingApp(List<Train> trains, BookingService bookingService) {
        super("Railway Ticket Management");
        this.trains = List.copyOf(trains);
        this.stations = trains.get(0).getRoute();
        this.bookingService = bookingService;
        Date today = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        this.journeyDatePicker = new JSpinner(new SpinnerDateModel(
            today,
            today,
            null,
            Calendar.DAY_OF_MONTH
        ));
        journeyDatePicker.setEditor(new JSpinner.DateEditor(journeyDatePicker, "EEE, dd MMM yyyy"));
        DefaultListCellRenderer stationRenderer = new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                String label = value instanceof Station station
                    ? station.name() + " (" + station.code() + ")"
                    : "";
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        };
        sourcePicker.setRenderer(stationRenderer);
        destinationPicker.setRenderer(stationRenderer);
        seatClassPicker.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                String label = value instanceof SeatClass seatClass
                    ? displaySeatClass(seatClass)
                    : "";
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        seatClassPicker.setSelectedItem(SeatClass.THIRD_AC);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 650));
        setSize(960, 740);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND);

        add(createHeader(), BorderLayout.NORTH);
        add(createTabs(), BorderLayout.CENTER);
        populateStations();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
                System.err.println("Could not apply system look and feel: " + e.getMessage());
            }

            List<Train> trains = Main.createSampleTrains();
            BookingService service = Main.createBookingService(trains);
            new RailwaySwingApp(trains, service).setVisible(true);
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(22, 30, 20, 30));

        JLabel title = new JLabel("Railway Ticket Management");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 25));

        JLabel subtitle = new JLabel("Find trains, reserve your journey, and manage your PNR");
        subtitle.setForeground(new Color(207, 220, 239));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(title);
        titles.add(Box.createVerticalStrut(6));
        titles.add(subtitle);
        header.add(titles, BorderLayout.CENTER);
        return header;
    }

    private JTabbedPane createTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.setBorder(BorderFactory.createEmptyBorder(12, 20, 18, 20));

        bookingCards.add(createSearchPanel(), SEARCH_CARD);
        bookingCards.add(createTrainsPanel(), TRAINS_CARD);
        bookingCards.add(createPassengerPanel(), PASSENGER_CARD);
        bookingCards.add(createReviewPanel(), REVIEW_CARD);
        bookingCards.add(createPaymentPanel(), PAYMENT_CARD);
        tabs.addTab("Book a ticket", bookingCards);
        tabs.addTab("Find or cancel ticket", createTicketPanel());
        return tabs;
    }

    private JPanel createSearchPanel() {
        JPanel panel = createStepPanel("1. Search your journey");
        JPanel form = createFormCard();
        GridBagConstraints constraints = createFormConstraints();
        addField(form, constraints, 0, "From", sourcePicker);
        addField(form, constraints, 1, "To", destinationPicker);
        addField(form, constraints, 2, "Journey date", journeyDatePicker);

        JButton searchButton = createPrimaryButton("Find available trains");
        searchButton.addActionListener(event -> showAvailableTrains());
        panel.add(form, BorderLayout.CENTER);
        panel.add(createButtonBar(null, searchButton), BorderLayout.SOUTH);

        sourcePicker.addActionListener(event -> updateDestinations());
        destinationPicker.addActionListener(event -> refreshTrainOptions());
        journeyDatePicker.addChangeListener(event -> refreshTrainOptions());
        return panel;
    }

    private JPanel createTrainsPanel() {
        JPanel panel = createStepPanel("2. Select an available train");
        JLabel instruction = mutedLabel("Train number, name, and available seats by class for your route and date.");
        trainPicker.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                String label = value instanceof TrainOption option ? option.toString() : "";
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        trainPicker.setPreferredSize(new Dimension(700, 54));
        trainPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        trainPicker.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(225, 231, 239)),
            BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));
        instruction.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        content.add(instruction);
        content.add(Box.createVerticalStrut(18));
        content.add(trainPicker);

        JButton backButton = new JButton("Back");
        backButton.addActionListener(event -> showCard(SEARCH_CARD));
        JButton continueButton = createPrimaryButton("Continue");
        continueButton.addActionListener(event -> chooseTrain());
        panel.add(content, BorderLayout.CENTER);
        panel.add(createButtonBar(backButton, continueButton), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createPassengerPanel() {
        JPanel panel = createStepPanel("3. Passenger details");
        JPanel form = createFormCard();
        GridBagConstraints constraints = createFormConstraints();
        addField(form, constraints, 0, "Passenger name", passengerName);
        addField(form, constraints, 1, "Age", passengerAge);
        addField(form, constraints, 2, "Berth preference", berthPicker);
        JLabel berthNote = mutedLabel(
            "* Berth preference is subject to availability; another berth may be assigned."
        );
        constraints.gridx = 1;
        constraints.gridy = 3;
        form.add(berthNote, constraints);
        addField(form, constraints, 4, "Class", seatClassPicker);
        classAvailabilityLabel.setForeground(BLUE);
        classAvailabilityLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        constraints.gridx = 1;
        constraints.gridy = 5;
        constraints.gridwidth = 1;
        form.add(classAvailabilityLabel, constraints);

        JButton backButton = new JButton("Back");
        backButton.addActionListener(event -> showCard(TRAINS_CARD));
        JButton continueButton = createPrimaryButton("Review journey");
        continueButton.addActionListener(event -> reviewBooking());
        seatClassPicker.addActionListener(event -> refreshClassAvailability());
        panel.add(form, BorderLayout.CENTER);
        panel.add(createButtonBar(backButton, continueButton), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createReviewPanel() {
        JPanel panel = createStepPanel("4. Review your booking");
        reviewDetails.setFont(new Font("SansSerif", Font.PLAIN, 15));
        reviewDetails.setForeground(NAVY);
        reviewDetails.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JPanel content = createFormCard();
        GridBagConstraints constraints = createFormConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        content.add(reviewDetails, constraints);

        JButton backButton = new JButton("Back to passenger details");
        backButton.addActionListener(event -> showCard(PASSENGER_CARD));
        JButton continueButton = createPrimaryButton("Confirm and continue to payment");
        continueButton.addActionListener(event -> showPayment());
        panel.add(content, BorderLayout.CENTER);
        panel.add(createButtonBar(backButton, continueButton), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createPaymentPanel() {
        JPanel panel = createStepPanel("5. Payment");
        paymentDetails.setFont(new Font("SansSerif", Font.PLAIN, 15));
        paymentDetails.setForeground(NAVY);
        paymentDetails.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JPanel content = createFormCard();
        GridBagConstraints constraints = createFormConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        content.add(paymentDetails, constraints);
        JButton noButton = new JButton("No, back to main page");
        noButton.addActionListener(event -> resetBooking());
        JButton yesButton = createPrimaryButton("Yes, pay and book");
        yesButton.addActionListener(event -> completeBooking());
        panel.add(content, BorderLayout.CENTER);
        panel.add(createButtonBar(noButton, yesButton), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createTicketPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));

        JPanel search = new JPanel(new GridBagLayout());
        search.setBackground(Color.WHITE);
        search.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(225, 231, 239)),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));
        ((AbstractDocument) pnrField.getDocument()).setDocumentFilter(new DigitsOnlyFilter());
        ((AbstractDocument) passengerName.getDocument()).setDocumentFilter(new LettersAndSpacesFilter());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 6, 5, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        search.add(fieldLabel("PNR"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        search.add(pnrField, constraints);

        JButton findButton = createPrimaryButton("Find ticket");
        findButton.addActionListener(event -> findTicket());
        constraints.gridx = 2;
        constraints.weightx = 0;
        search.add(findButton, constraints);

        ticketDetails.setEditable(false);
        ticketDetails.setLineWrap(true);
        ticketDetails.setWrapStyleWord(true);
        ticketDetails.setFont(new Font("Monospaced", Font.PLAIN, 13));
        ticketDetails.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JScrollPane detailsScroll = new JScrollPane(ticketDetails);
        detailsScroll.setBorder(BorderFactory.createLineBorder(new Color(225, 231, 239)));

        JButton cancelButton = new JButton("Cancel ticket");
        cancelButton.addActionListener(event -> cancelTicket());
        JPanel actions = createButtonBar(null, cancelButton);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(search, BorderLayout.NORTH);
        content.add(detailsScroll, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void populateStations() {
        sourcePicker.setModel(new DefaultComboBoxModel<>(stations.toArray(Station[]::new)));
        updateDestinations();
    }

    private void updateDestinations() {
        Station source = (Station) sourcePicker.getSelectedItem();
        if (source == null) {
            return;
        }
        List<Station> possibleDestinations = stations.stream()
            .filter(station -> !station.equals(source))
            .toList();
        destinationPicker.setModel(
            new DefaultComboBoxModel<>(possibleDestinations.toArray(Station[]::new))
        );
        refreshTrainOptions();
    }

    private void showAvailableTrains() {
        if (sourcePicker.getSelectedItem() == destinationPicker.getSelectedItem()) {
            showError("Choose different departure and destination stations.");
            return;
        }
        refreshTrainOptions();
        if (trainPicker.getItemCount() == 0) {
            showError("No trains run in this direction. Choose another route.");
            return;
        }
        showCard(TRAINS_CARD);
    }

    private void refreshTrainOptions() {
        Station source = (Station) sourcePicker.getSelectedItem();
        Station destination = (Station) destinationPicker.getSelectedItem();
        if (source == null || destination == null) {
            trainPicker.setModel(new DefaultComboBoxModel<>());
            return;
        }

        LocalDate journeyDate = selectedJourneyDate();
        String selectedTrainNumber = selectedTrain == null ? null : selectedTrain.getTrainNumber();
        DefaultComboBoxModel<TrainOption> options = new DefaultComboBoxModel<>();
        for (Train train : trains) {
            int fromIndex = train.getRoute().indexOf(source);
            int toIndex = train.getRoute().indexOf(destination);
            if (fromIndex >= 0
                    && toIndex > fromIndex
                    && train.getDepartureDateTime(journeyDate).isAfter(java.time.LocalDateTime.now())) {
                Map<SeatClass, Long> availableByClass = new EnumMap<>(SeatClass.class);
                Map<SeatClass, Integer> waitlistByClass = new EnumMap<>(SeatClass.class);
                for (SeatClass seatClass : SeatClass.values()) {
                    availableByClass.put(
                        seatClass,
                        train.getAvailableSeatCount(seatClass, source, destination, journeyDate)
                    );
                    waitlistByClass.put(
                        seatClass,
                        train.getWaitlistSize(journeyDate, seatClass)
                    );
                }
                options.addElement(new TrainOption(train, availableByClass, waitlistByClass));
            }
        }
        trainPicker.setModel(options);
        for (int index = 0; index < options.getSize(); index++) {
            if (options.getElementAt(index).train().getTrainNumber().equals(selectedTrainNumber)) {
                trainPicker.setSelectedIndex(index);
                return;
            }
        }
    }

    private void chooseTrain() {
        TrainOption option = (TrainOption) trainPicker.getSelectedItem();
        if (option == null) {
            showError("Select an available train.");
            return;
        }
        selectedTrain = option.train();
        selectedSeatClass = (SeatClass) seatClassPicker.getSelectedItem();
        refreshClassAvailability();
        showCard(PASSENGER_CARD);
    }

    private void reviewBooking() {
        String name = passengerName.getText().trim();
        if (name.isEmpty()) {
            showError("Enter the passenger's name.");
            return;
        }
        try {
            int age = Integer.parseInt(passengerAge.getText().trim());
            if (age < 0 || age > 120) {
                showError("Age must be between 0 and 120.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Enter a valid whole-number age.");
            return;
        }
        if (selectedTrain == null) {
            showError("Select a train before entering passenger details.");
            showCard(TRAINS_CARD);
            return;
        }
        selectedSeatClass = (SeatClass) seatClassPicker.getSelectedItem();
        if (selectedSeatClass == null) {
            showError("Select a travel class.");
            return;
        }

        Station source = (Station) sourcePicker.getSelectedItem();
        Station destination = (Station) destinationPicker.getSelectedItem();
        long distanceKm = Math.abs(destination.distanceKm() - source.distanceKm());
        double fare = bookingService.calculateFare(source, destination, selectedSeatClass);
        reviewDetails.setText("<html>"
            + "<b>Passenger:</b> " + escapeHtml(name) + "<br>"
            + "<b>Age:</b> " + passengerAge.getText().trim() + "<br>"
            + "<b>Berth preference:</b> " + berthPicker.getSelectedItem()
            + " (subject to availability)<br>"
            + "<b>Train:</b> " + selectedTrain.getTrainNumber() + " - "
            + escapeHtml(selectedTrain.getTrainName()) + "<br>"
            + "<b>Departure:</b> "
            + selectedTrain.getDepartureDateTime(selectedJourneyDate()).format(DEPARTURE_FORMAT) + "<br>"
            + "<b>Journey:</b> " + escapeHtml(source.name()) + " -> "
            + escapeHtml(destination.name()) + "<br>"
            + "<b>Journey date:</b> " + selectedJourneyDate().format(DATE_FORMAT) + "<br>"
            + "<b>Distance:</b> " + distanceKm + " km<br>"
            + "<b>Class:</b> " + displaySeatClass(selectedSeatClass) + "<br>"
            + "<b>Fare:</b> INR " + String.format(Locale.ROOT, "%.2f", fare)
            + "</html>");
        showCard(REVIEW_CARD);
    }

    private void showPayment() {
        Station source = (Station) sourcePicker.getSelectedItem();
        Station destination = (Station) destinationPicker.getSelectedItem();
        double fare = bookingService.calculateFare(source, destination, selectedSeatClass);
        paymentDetails.setText("<html>"
            + "<b>Train:</b> " + selectedTrain.getTrainNumber() + " - "
            + escapeHtml(selectedTrain.getTrainName()) + "<br>"
            + "<b>Passenger:</b> " + escapeHtml(passengerName.getText().trim()) + "<br>"
            + "<b>Journey date:</b> " + selectedJourneyDate().format(DATE_FORMAT) + "<br>"
            + "<b>Departure:</b> "
            + selectedTrain.getDepartureDateTime(selectedJourneyDate()).format(DEPARTURE_FORMAT) + "<br>"
            + "<b>Class:</b> " + displaySeatClass(selectedSeatClass) + "<br><br>"
            + "<font color='#2563ad' size='+2'><b>Amount due: INR "
            + String.format(Locale.ROOT, "%.2f", fare) + "</b></font>"
            + "</html>");
        showCard(PAYMENT_CARD);
    }

    private void completeBooking() {
        if (selectedTrain == null) {
            showError("Select a train before confirming payment.");
            resetBooking();
            return;
        }
        Station source = (Station) sourcePicker.getSelectedItem();
        Station destination = (Station) destinationPicker.getSelectedItem();
        Passenger passenger = new Passenger(
            UUID.randomUUID().toString(),
            passengerName.getText().trim(),
            Integer.parseInt(passengerAge.getText().trim()),
            (BerthPreference) berthPicker.getSelectedItem()
        );
        try {
            Ticket ticket = bookingService.bookTicket(
                selectedTrain.getTrainNumber(),
                source,
                destination,
                List.of(passenger),
                selectedSeatClass,
                selectedJourneyDate()
            );
            showTicket(ticket);
            pnrField.setText(ticket.getPnr());
            JOptionPane.showMessageDialog(
                this,
                "Payment confirmed.\nBooking " + ticket.getStatus()
                    + "\nPNR: " + ticket.getPnr(),
                "Booking complete",
                JOptionPane.INFORMATION_MESSAGE
            );
            resetBooking();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError("Booking failed: " + e.getMessage());
            refreshTrainOptions();
            showCard(TRAINS_CARD);
        }
    }

    private void findTicket() {
        String pnr = pnrField.getText().trim();
        if (!pnr.matches("\\d+")) {
            showError("Enter the PNR shown on your ticket.");
            return;
        }
        Optional<Ticket> ticket = bookingService.getTicket(pnr);
        if (ticket.isEmpty()) {
            ticketDetails.setText("");
            showError("No ticket found for PNR " + pnr + ".");
            return;
        }
        showTicket(ticket.get());
    }

    private void cancelTicket() {
        String pnr = pnrField.getText().trim();
        if (!pnr.matches("\\d+")) {
            showError("Enter the PNR shown on your ticket.");
            return;
        }
        CancellationResult quote;
        try {
            quote = bookingService.previewCancellation(pnr);
        } catch (IllegalArgumentException e) {
            showError("Cancellation failed: " + e.getMessage());
            return;
        }
        int confirmation = JOptionPane.showConfirmDialog(
            this,
            cancellationPrompt(quote),
            "Confirm cancellation",
            JOptionPane.YES_NO_OPTION
        );
        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CancellationResult result = bookingService.cancelTicket(pnr);
            bookingService.getTicket(pnr).ifPresent(this::showTicket);
            JOptionPane.showMessageDialog(
                this,
                String.format(
                    Locale.ROOT,
                    "Ticket cancelled.\nCancellation charge: INR %.2f (%.0f%%)"
                        + "\nRefund amount: INR %.2f"
                        + "\nAny eligible waitlisted passenger may be promoted.",
                    result.cancellationCharge(),
                    result.chargePercentage(),
                    result.refundAmount()
                ),
                "Cancellation complete",
                JOptionPane.INFORMATION_MESSAGE
            );
            refreshTrainOptions();
        } catch (IllegalArgumentException e) {
            showError("Cancellation failed: " + e.getMessage());
        }
    }

    private String cancellationPrompt(CancellationResult quote) {
        return String.format(
            Locale.ROOT,
            "Cancel ticket %s?\nDeparture: %s\nTime remaining: %s"
                + "\nFare paid: INR %.2f\nCancellation charge: INR %.2f (%.0f%%)"
                + "\nRefund: INR %.2f\n\nContinue?",
            quote.pnr(),
            quote.departureDateTime().format(DEPARTURE_FORMAT),
            formatDuration(quote.timeUntilDeparture()),
            quote.originalFare(),
            quote.cancellationCharge(),
            quote.chargePercentage(),
            quote.refundAmount()
        );
    }

    private String formatDuration(java.time.Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        return hours + " hours " + minutes + " minutes";
    }

    private void showTicket(Ticket ticket) {
        Train train = trains.stream()
            .filter(candidate -> candidate.getTrainNumber().equals(ticket.getTrainNumber()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Ticket refers to an unknown train."));
        long distanceKm = Math.abs(
            ticket.getDestination().distanceKm() - ticket.getSource().distanceKm()
        );
        StringBuilder details = new StringBuilder()
            .append("PNR:          ").append(ticket.getPnr()).append('\n')
            .append("Status:      ").append(ticket.getStatus()).append('\n')
            .append("Train:       ").append(ticket.getTrainNumber())
            .append(" - ").append(train.getTrainName()).append('\n')
            .append("Journey:     ").append(ticket.getSource().name())
            .append(" -> ").append(ticket.getDestination().name()).append('\n')
            .append("Date:        ").append(ticket.getJourneyDate().format(DATE_FORMAT)).append('\n')
            .append("Departure:   ").append(ticket.getDepartureDateTime().format(DEPARTURE_FORMAT)).append('\n')
            .append("Distance:    ").append(distanceKm).append(" km\n")
            .append("Class:       ").append(displaySeatClass(ticket.getSeatClass())).append('\n')
            .append("Fare:        INR ")
            .append(String.format(Locale.ROOT, "%.2f", ticket.getTotalFare()))
            .append('\n');
        if (ticket.getStatus() == BookingStatus.CANCELLED) {
            details.append("Cancel fee:  INR ")
                .append(String.format(Locale.ROOT, "%.2f", ticket.getCancellationCharge())).append('\n')
                .append("Refund:      INR ")
                .append(String.format(Locale.ROOT, "%.2f", ticket.getRefundAmount())).append('\n');
        }
        ticket.getBookedSeats().forEach((passenger, seat) -> {
            details.append("Passenger:   ").append(passenger.fullName()).append('\n');
            if (seat != null) {
                details.append("Seat:        ").append(seat.getSeatNumber())
                    .append(" (").append(seat.getBerthType()).append(")\n");
            } else if (ticket.getStatus() == BookingStatus.WAITLISTED) {
                details.append("Seat:        Waitlisted\n");
            }
        });
        ticketDetails.setText(details.toString());
        ticketDetails.setCaretPosition(0);
    }

    private void resetBooking() {
        passengerName.setText("");
        passengerAge.setText("");
        selectedTrain = null;
        selectedSeatClass = null;
        seatClassPicker.setSelectedItem(SeatClass.THIRD_AC);
        populateStations();
        showCard(SEARCH_CARD);
    }

    private LocalDate selectedJourneyDate() {
        Date date = (Date) journeyDatePicker.getValue();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void showCard(String card) {
        bookingLayout.show(bookingCards, card);
    }

    private void refreshClassAvailability() {
        Station source = (Station) sourcePicker.getSelectedItem();
        Station destination = (Station) destinationPicker.getSelectedItem();
        SeatClass seatClass = (SeatClass) seatClassPicker.getSelectedItem();
        if (selectedTrain == null || source == null || destination == null || seatClass == null) {
            classAvailabilityLabel.setText("");
            return;
        }
        long available = selectedTrain.getAvailableSeatCount(
            seatClass,
            source,
            destination,
            selectedJourneyDate()
        );
        int waiting = selectedTrain.getWaitlistSize(selectedJourneyDate(), seatClass);
        classAvailabilityLabel.setText(
            displaySeatClass(seatClass) + " seats available: " + available
                + " of 3  |  Waitlist: " + waiting
        );
    }

    private JPanel createStepPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 19));
        heading.setForeground(NAVY);
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private JPanel createFormCard() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(225, 231, 239)),
            BorderFactory.createEmptyBorder(22, 24, 22, 24)
        ));
        return form;
    }

    private GridBagConstraints createFormConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 8, 10, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        return constraints;
    }

    private void addField(
        JPanel panel,
        GridBagConstraints constraints,
        int row,
        String label,
        JComponent input
    ) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(fieldLabel(label), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(input, constraints);
    }

    private JPanel createButtonBar(JButton backButton, JButton continueButton) {
        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        if (backButton != null) {
            actions.add(backButton, BorderLayout.WEST);
        }
        if (continueButton != null) {
            actions.add(continueButton, BorderLayout.EAST);
        }
        return actions;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setForeground(NAVY);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        return label;
    }

    private JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setBackground(BLUE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(24, 73, 132)),
            BorderFactory.createEmptyBorder(9, 17, 9, 17)
        ));
        button.setMinimumSize(new Dimension(150, 40));
        return button;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Please check", JOptionPane.ERROR_MESSAGE);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static String displaySeatClass(SeatClass seatClass) {
        return switch (seatClass) {
            case FIRST_AC -> "First AC";
            case SECOND_AC -> "Second AC";
            case THIRD_AC -> "Third AC";
            case SLEEPER -> "Sleeper";
            case GENERAL -> "General";
        };
    }

    private record TrainOption(
        Train train,
        Map<SeatClass, Long> availableByClass,
        Map<SeatClass, Integer> waitlistByClass
    ) {
        @Override
        public String toString() {
            StringBuilder option = new StringBuilder("<html><b>")
                .append(train.getTrainNumber()).append(" - ")
                .append(train.getTrainName()).append("</b>")
                .append(" (Departs ").append(train.getDepartureTime()).append(")<br>");
            for (SeatClass seatClass : SeatClass.values()) {
                option.append(displaySeatClass(seatClass))
                    .append(": ").append(availableByClass.get(seatClass))
                    .append(" available");
                int waiting = waitlistByClass.get(seatClass);
                if (waiting > 0) {
                    option.append(" (waitlist ").append(waiting).append(')');
                }
                option.append(" &nbsp; ");
            }
            return option.append("</html>").toString();
        }
    }

    private static final class DigitsOnlyFilter extends DocumentFilter {
        @Override
        public void insertString(
            FilterBypass bypass,
            int offset,
            String text,
            AttributeSet attributes
        ) throws BadLocationException {
            if (text != null && text.matches("\\d+")) {
                super.insertString(bypass, offset, text, attributes);
            }
        }

        @Override
        public void replace(
            FilterBypass bypass,
            int offset,
            int length,
            String text,
            AttributeSet attributes
        ) throws BadLocationException {
            if (text == null || text.isEmpty() || text.matches("\\d+")) {
                super.replace(bypass, offset, length, text, attributes);
            }
        }
    }

    private static final class LettersAndSpacesFilter extends DocumentFilter {
        @Override
        public void insertString(
            FilterBypass bypass,
            int offset,
            String text,
            AttributeSet attributes
        ) throws BadLocationException {
            if (text != null && containsOnlyLettersAndSpaces(text)) {
                super.insertString(bypass, offset, text, attributes);
            }
        }

        @Override
        public void replace(
            FilterBypass bypass,
            int offset,
            int length,
            String text,
            AttributeSet attributes
        ) throws BadLocationException {
            if (text == null || text.isEmpty() || containsOnlyLettersAndSpaces(text)) {
                super.replace(bypass, offset, length, text, attributes);
            }
        }

        private boolean containsOnlyLettersAndSpaces(String text) {
            return text.codePoints().allMatch(codePoint ->
                Character.isLetter(codePoint) || Character.isWhitespace(codePoint)
            );
        }
    }
}
