package com.railway.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Seat {
    private final String seatNumber;
    private final BerthPreference berthType;
    private final SeatClass seatClass;
    private final Map<LocalDate, Set<Integer>> bookedSegmentsByDate = new HashMap<>();

    public Seat(String seatNumber, BerthPreference berthType, SeatClass seatClass) {
        this.seatNumber = seatNumber;
        this.berthType = berthType;
        this.seatClass = seatClass;
    }

    public synchronized boolean reserve(int fromSegment, int toSegment) {
        return reserve(LocalDate.now(), fromSegment, toSegment);
    }

    public synchronized boolean reserve(LocalDate journeyDate, int fromSegment, int toSegment) {
        if (fromSegment < 0 || toSegment <= fromSegment) {
            throw new IllegalArgumentException("Journey must cover at least one route segment.");
        }
        Set<Integer> bookedSegments = bookedSegmentsByDate.computeIfAbsent(
            journeyDate,
            ignored -> new HashSet<>()
        );
        for (int segment = fromSegment; segment < toSegment; segment++) {
            if (bookedSegments.contains(segment)) {
                return false;
            }
        }
        for (int segment = fromSegment; segment < toSegment; segment++) {
            bookedSegments.add(segment);
        }
        return true;
    }

    public synchronized void release(int fromSegment, int toSegment) {
        release(LocalDate.now(), fromSegment, toSegment);
    }

    public synchronized void release(LocalDate journeyDate, int fromSegment, int toSegment) {
        Set<Integer> bookedSegments = bookedSegmentsByDate.get(journeyDate);
        if (bookedSegments == null) {
            return;
        }
        for (int segment = fromSegment; segment < toSegment; segment++) {
            bookedSegments.remove(segment);
        }
        if (bookedSegments.isEmpty()) {
            bookedSegmentsByDate.remove(journeyDate);
        }
    }

    public synchronized boolean isAvailable() {
        return bookedSegmentsByDate.values().stream().allMatch(Set::isEmpty);
    }

    public synchronized boolean isAvailable(int fromSegment, int toSegment) {
        return isAvailable(LocalDate.now(), fromSegment, toSegment);
    }

    public synchronized boolean isAvailable(LocalDate journeyDate, int fromSegment, int toSegment) {
        Set<Integer> bookedSegments = bookedSegmentsByDate.getOrDefault(journeyDate, Set.of());
        for (int segment = fromSegment; segment < toSegment; segment++) {
            if (bookedSegments.contains(segment)) {
                return false;
            }
        }
        return true;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public BerthPreference getBerthType() {
        return berthType;
    }

    public SeatClass getSeatClass() {
        return seatClass;
    }
}
