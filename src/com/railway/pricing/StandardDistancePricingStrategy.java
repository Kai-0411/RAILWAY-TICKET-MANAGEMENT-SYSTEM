package com.railway.pricing;

import com.railway.model.SeatClass;
import com.railway.model.Station;

public class StandardDistancePricingStrategy implements PricingStrategy {
    private static final double BASE_RATE_PER_KM = 1.25;

    @Override
    public double calculateFare(Station source, Station destination, SeatClass seatClass) {
        int distance = Math.abs(destination.distanceKm() - source.distanceKm());
        if (distance == 0) {
            throw new IllegalArgumentException("Source and Destination stations cannot be identical.");
        }
        return distance * BASE_RATE_PER_KM * seatClass.getFareMultiplier();
    }
}
