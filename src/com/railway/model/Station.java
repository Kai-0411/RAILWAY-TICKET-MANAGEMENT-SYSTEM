package com.railway.model;

public record Station(String code, String name, int distanceKm)
    {
    public Station 
    {
        if (code == null || code.isBlank()) 
        {
            throw new IllegalArgumentException("Station code cannot be null or empty.");
        }
    }
}
