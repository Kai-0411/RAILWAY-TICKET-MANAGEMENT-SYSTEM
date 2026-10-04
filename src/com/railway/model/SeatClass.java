package com.railway.model;

public enum SeatClass {
    FIRST_AC(1.8),
    SECOND_AC(1.4),
    THIRD_AC(1.1),
    SLEEPER(1.0),
    GENERAL(0.6);

    private final double fareMultiplier;

    SeatClass(double fareMultiplier) {
        this.fareMultiplier = fareMultiplier;
    }

    public double getFareMultiplier() {
        return fareMultiplier;
    }
}