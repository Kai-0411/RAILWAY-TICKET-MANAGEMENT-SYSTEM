package com.railway.model;

import java.util.HashSet;
import java.util.Set;

public class Seat {
    private final String seatNumber;
    private final BerthPreference berthType;
    private final SeatClass seatClass;
    private final Set<Integer> bookedSegments = new HashSet<>();

    public Seat(String seatNumber, BerthPreference berthType, SeatClass seatClass) {
        this.seatNumber = seatNumber;
        this.berthType = berthType;
        this.seatClass = seatClass;
    }

    public synchronized boolean reserve(int fromSegment, int toSegment) {
        if (fromSegment < 0 || toSegment <= fromSegment) {
            throw new IllegalArgumentException("Journey must cover at least one route segment.");
        }
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
        for (int segment = fromSegment; segment < toSegment; segment++) {
            bookedSegments.remove(segment);
        }
    }

    public synchronized boolean isAvailable() {
        return bookedSegments.isEmpty();
    }

    public synchronized boolean isAvailable(int fromSegment, int toSegment) {
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
