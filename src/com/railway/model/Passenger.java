package com.railway.model;

public record Passenger(String id, String fullName, int age, BerthPreference preference) {
    public Passenger {
        if (age < 0 || age > 120) {
            throw new IllegalArgumentException("Invalid passenger age.");
        }
    }
}
