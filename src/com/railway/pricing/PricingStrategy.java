package com.railway.pricing;

import com.railway.model.SeatClass;
import com.railway.model.Station;

public interface PricingStrategy {
    double calculateFare(Station source, Station destination, SeatClass seatClass);
}
