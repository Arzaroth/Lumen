package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.FileInputStream;

public class HeavensTest {
    /** 2026-10-24 20:00 UTC: Paris at 22:00, two days before the full Moon. */
    private static final long TONIGHT = 1792872000000L;
    private static final double PARIS_LAT = 48.85, PARIS_LON = 2.35;

    private static double azimuthGap(double a, double b) {
        return Math.abs(Heavens.norm(a - b + 180) - 180);
    }

    @Test
    public void siderealTimeAtTheEpoch() {
        long j2000 = Math.round((2451545.0 - 2440587.5) * 86_400_000.0);
        assertEquals(280.46, Heavens.siderealTime(j2000, 0), 0.01);
        assertEquals(Heavens.norm(280.46 + 2.35), Heavens.siderealTime(j2000, 2.35), 0.01);
    }

    @Test
    public void polarisStandsAtTheLatitude() {
        double lst = Heavens.siderealTime(TONIGHT, PARIS_LON);
        double[] polaris = Heavens.horizontal(37.95, 89.26, PARIS_LAT, lst);
        assertEquals(PARIS_LAT, polaris[0], 1.0);
        assertTrue(azimuthGap(polaris[1], 0) < 1.5);
    }

    @Test
    public void horizontalAndEquatorialAreInverses() {
        double lst = Heavens.siderealTime(TONIGHT, PARIS_LON);
        double[] hz = Heavens.horizontal(297.7, 8.87, PARIS_LAT, lst);
        double[] eq = Heavens.equatorial(hz[0], hz[1], PARIS_LAT, lst);
        assertEquals(297.7, eq[0], 1e-6);
        assertEquals(8.87, eq[1], 1e-6);
    }

    @Test
    public void altairIsLowInTheWestSouthWestTonight() {
        double[] altair = Heavens.horizontal(297.7, 8.87, PARIS_LAT, Heavens.siderealTime(TONIGHT, PARIS_LON));
        System.out.println("ALTAIR alt " + altair[0] + " az " + altair[1]);
        assertTrue(altair[1] > 200 && altair[1] < 280);
    }

    @Test
    public void theGalacticCentreIsOnTheGalacticPlane() {
        double[] centre = Heavens.galactic(266.405, -28.936);
        assertEquals(0, centre[0], 0.1);
        assertTrue(azimuthGap(centre[1], 0) < 0.2);
        assertEquals(90, Heavens.galactic(192.85948, 27.12825)[0], 0.01);
    }

    @Test
    public void theSunAndTheMoonKeepTheirAppointments() {
        long equinox = 1710903960000L;
        double[] sun = Heavens.body(Heavens.SUN, equinox);
        assertTrue(azimuthGap(sun[0], 0) < 1);
        assertEquals(0, sun[1], 0.5);
        assertTrue(Heavens.moonPhase(1706205240000L)[0] > 0.97);
        assertTrue(Heavens.moonPhase(1707519540000L)[0] < 0.03);
        double[] tonight = Heavens.moonPhase(TONIGHT);
        assertTrue(tonight[0] > 0.9);
        assertEquals(1, tonight[1], 0);
    }

    @Test
    public void jupiterAtItsDecember2024Opposition() {
        double[] jupiter = Heavens.body(Heavens.JUPITER, 1733572800000L);
        assertTrue(azimuthGap(jupiter[0], 74) < 3);
        assertEquals(22, jupiter[1], 3);
        double[] saturn = Heavens.body(Heavens.SATURN, 1733572800000L);
        assertTrue(saturn[0] > 330 && saturn[0] < 360);
        assertTrue(Heavens.body(Heavens.VENUS, TONIGHT)[1] < 90);
        assertTrue(Heavens.body(Heavens.MARS, TONIGHT)[1] < 90);
        assertTrue(Heavens.body(Heavens.MERCURY, TONIGHT)[1] < 90);
    }

    @Test
    public void theCatalogueStartsWithSirius() throws Exception {
        float[] stars = Heavens.catalog(new FileInputStream("assets/stars.bin"));
        assertEquals(9096 * 4, stars.length);
        assertEquals(101.29, stars[0], 0.01);
        assertEquals(-16.72, stars[1], 0.01);
        assertEquals(-1.46, stars[2], 0.01);
    }
}
