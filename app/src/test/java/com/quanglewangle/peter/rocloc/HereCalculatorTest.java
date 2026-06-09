package com.quanglewangle.peter.rocloc;

import com.quanglewangle.peter.rocloc.location.HereCalculator;

import org.junit.Test;
import static org.junit.Assert.*;

public class HereCalculatorTest {

    @Test
    public void distanceZeroForSamePoint() {
        double d = HereCalculator.distanceKm(51.5, -0.1, 51.5, -0.1);
        assertEquals(0.0, d, 0.001);
    }

    @Test
    public void maidenheadLondonIsIO91() {
        String grid = HereCalculator.maidenhead(51.5, -0.1);
        assertTrue("London grid should start IO91, got " + grid, grid.startsWith("IO91"));
    }

    @Test
    public void bearingNorthward() {
        double b = HereCalculator.bearingDeg(51.0, 0.0, 52.0, 0.0);
        assertEquals(0.0, b, 1.0);
    }

    @Test
    public void compassPointNorth() {
        assertEquals("N", HereCalculator.compassPoint(0));
        assertEquals("N", HereCalculator.compassPoint(359));
    }

    @Test
    public void compassPointEast() {
        assertEquals("E", HereCalculator.compassPoint(90));
    }
}
