package com.railway.model;

import java.time.Duration;
import java.time.LocalDateTime;

public record CancellationResult(
    String pnr,
    LocalDateTime departureDateTime,
    Duration timeUntilDeparture,
    double originalFare,
    double chargePercentage,
    double cancellationCharge,
    double refundAmount
) {
}
