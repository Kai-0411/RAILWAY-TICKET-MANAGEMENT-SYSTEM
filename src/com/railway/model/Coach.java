package com.railway.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class Coach 
{
    private final String coachId;
    private final SeatClass seatClass;
    private final List<Seat> seats;
    private final ReentrantLock coachLock = new ReentrantLock();

    public Coach(String coachId, SeatClass seatClass, int totalSeats) 
    {
        this.coachId = coachId;
        this.seatClass = seatClass;
        this.seats = initializeSeats(totalSeats);
    }

    private List<Seat> initializeSeats(int count) 
    {
        List<Seat> seatList = new ArrayList<>();
        BerthPreference[] berths = 
        {
            BerthPreference.LOWER,
            BerthPreference.MIDDLE,
            BerthPreference.UPPER,
            BerthPreference.SIDE_LOWER,
            BerthPreference.SIDE_UPPER
        };

        for (int i = 1; i <= count; i++) 
        {
            BerthPreference berth = berths[(i - 1) % berths.length];
            seatList.add(new Seat(coachId + "-" + i, berth, seatClass));
        }
        return Collections.unmodifiableList(seatList);
    }

    public Optional<Seat> allocateSeat(BerthPreference preference, int fromSegment, int toSegment) 
    {
        return allocateSeat(preference, LocalDate.now(), fromSegment, toSegment);
    }

    public Optional<Seat> allocateSeat(
        BerthPreference preference,
        LocalDate journeyDate,
        int fromSegment,
        int toSegment
    ) 
    {
        coachLock.lock();
        try 
        {
            if (preference != BerthPreference.NO_PREFERENCE) 
            {
                for (Seat seat : seats) 
                {
                    if (seat.getBerthType() == preference
                            && seat.reserve(journeyDate, fromSegment, toSegment)) 
                    {
                        return Optional.of(seat);
                    }
                }
            }
            for (Seat seat : seats) 
            {
                if (seat.reserve(journeyDate, fromSegment, toSegment)) 
                {
                    return Optional.of(seat);
                }
            }
            return Optional.empty();
        } 
        finally 
        {
            coachLock.unlock();
        }
    }

    public long getAvailableSeatCount(int fromSegment, int toSegment) 
    {
        return getAvailableSeatCount(LocalDate.now(), fromSegment, toSegment);
    }

    public long getAvailableSeatCount(LocalDate journeyDate, int fromSegment, int toSegment) 
    {
        return seats.stream()
            .filter(seat -> seat.isAvailable(journeyDate, fromSegment, toSegment))
            .count();
    }

    public String getCoachId()
    {
        return coachId;
    }

    public SeatClass getSeatClass() 
    {
        return seatClass;
    }

    public List<Seat> getSeats() 
    {
        return seats;
    }
}
