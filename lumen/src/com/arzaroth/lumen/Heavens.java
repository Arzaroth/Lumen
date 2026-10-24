package com.arzaroth.lumen;

/**
 * The real sky's arithmetic: where a star, the Sun, the Moon or a planet stands for someone at a
 * latitude and longitude at a given instant. Planets and the Moon follow Paul Schlyter's simplified
 * orbital elements ("How to compute planetary positions"), good to about a degree, plenty for a sky
 * drawn at twenty pixels a degree. Angles are in degrees unless a name says otherwise.
 */
final class Heavens {
    static final int SUN = 0, MOON = 1, MERCURY = 2, VENUS = 3, MARS = 4, JUPITER = 5, SATURN = 6;

    private Heavens() {
    }

    /** Days since 2000 January 0.0 UT, the epoch of the orbital elements. */
    static double day(long millis) {
        return millis / 86_400_000.0 + 2440587.5 - 2451543.5;
    }

    /** Local sidereal time, the right ascension on the meridian, in degrees. */
    static double siderealTime(long millis, double longitude) {
        double jd = millis / 86_400_000.0 + 2440587.5;
        return norm(280.46061837 + 360.98564736629 * (jd - 2451545.0) + longitude);
    }

    /** Altitude and azimuth (from north, through east) of a right ascension and declination. */
    static double[] horizontal(double ra, double dec, double latitude, double sidereal) {
        double ha = Math.toRadians(sidereal - ra), d = Math.toRadians(dec), lat = Math.toRadians(latitude);
        double sinAlt = Math.sin(d) * Math.sin(lat) + Math.cos(d) * Math.cos(lat) * Math.cos(ha);
        double alt = Math.asin(Math.max(-1, Math.min(1, sinAlt)));
        double az = Math.atan2(-Math.sin(ha) * Math.cos(d), Math.cos(lat) * Math.sin(d) - Math.sin(lat) * Math.cos(d) * Math.cos(ha));
        return new double[] {Math.toDegrees(alt), norm(Math.toDegrees(az))};
    }

    /** Right ascension and declination of an altitude and azimuth: `horizontal` backwards. */
    static double[] equatorial(double alt, double az, double latitude, double sidereal) {
        double a = Math.toRadians(alt), z = Math.toRadians(az), lat = Math.toRadians(latitude);
        double sinDec = Math.sin(a) * Math.sin(lat) + Math.cos(a) * Math.cos(lat) * Math.cos(z);
        double dec = Math.asin(Math.max(-1, Math.min(1, sinDec)));
        double ha = Math.atan2(-Math.sin(z) * Math.cos(a), Math.cos(lat) * Math.sin(a) - Math.sin(lat) * Math.cos(a) * Math.cos(z));
        return new double[] {norm(sidereal - Math.toDegrees(ha)), Math.toDegrees(dec)};
    }

    /** Galactic latitude and longitude of a right ascension and declination (J2000). */
    static double[] galactic(double ra, double dec) {
        double a = Math.toRadians(ra - 192.85948), d = Math.toRadians(dec), pole = Math.toRadians(27.12825);
        double sinB = Math.sin(d) * Math.sin(pole) + Math.cos(d) * Math.cos(pole) * Math.cos(a);
        double l = 122.93192 - Math.toDegrees(Math.atan2(Math.cos(d) * Math.sin(a),
                Math.sin(d) * Math.cos(pole) - Math.cos(d) * Math.sin(pole) * Math.cos(a)));
        return new double[] {Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinB)))), norm(l)};
    }

    /** Right ascension, declination and ecliptic longitude of one of the bodies, seen from Earth. */
    static double[] body(int which, long millis) {
        double d = day(millis);
        double ecl = Math.toRadians(23.4393 - 3.563e-7 * d);
        double[] sun = sun(d);
        double x, y, z;
        if (which == SUN) {
            x = sun[0];
            y = sun[1];
            z = 0;
        } else if (which == MOON) {
            double[] m = orbit(125.1228 - 0.0529538083 * d, 5.1454, 318.0634 + 0.1643573223 * d, 60.2666, 0.054900,
                    115.3654 + 13.0649929509 * d);
            x = m[0];
            y = m[1];
            z = m[2];
        } else {
            double[] p = planet(which, d);
            x = p[0] + sun[0];
            y = p[1] + sun[1];
            z = p[2];
        }
        double lon = norm(Math.toDegrees(Math.atan2(y, x)));
        double ye = y * Math.cos(ecl) - z * Math.sin(ecl), ze = y * Math.sin(ecl) + z * Math.cos(ecl);
        double ra = norm(Math.toDegrees(Math.atan2(ye, x)));
        double dec = Math.toDegrees(Math.atan2(ze, Math.sqrt(x * x + ye * ye)));
        return new double[] {ra, dec, lon};
    }

    /** How much of the Moon is lit, from 0 (new) to 1 (full), and whether it is waxing. */
    static double[] moonPhase(long millis) {
        double elongation = norm(body(MOON, millis)[2] - body(SUN, millis)[2]);
        return new double[] {(1 - Math.cos(Math.toRadians(elongation))) / 2, elongation < 180 ? 1 : 0};
    }

    /** The Sun's geocentric ecliptic position, in astronomical units. */
    private static double[] sun(double d) {
        double w = 282.9404 + 4.70935e-5 * d, e = 0.016709 - 1.151e-9 * d, m = 356.0470 + 0.9856002585 * d;
        double[] p = orbit(0, 0, w, 1.000000, e, m);
        return new double[] {p[0], p[1]};
    }

    private static double[] planet(int which, double d) {
        switch (which) {
            case MERCURY:
                return orbit(48.3313 + 3.24587e-5 * d, 7.0047 + 5.00e-8 * d, 29.1241 + 1.01444e-5 * d, 0.387098,
                        0.205635 + 5.59e-10 * d, 168.6562 + 4.0923344368 * d);
            case VENUS:
                return orbit(76.6799 + 2.46590e-5 * d, 3.3946 + 2.75e-8 * d, 54.8910 + 1.38374e-5 * d, 0.723330,
                        0.006773 - 1.302e-9 * d, 48.0052 + 1.6021302244 * d);
            case MARS:
                return orbit(49.5574 + 2.11081e-5 * d, 1.8497 - 1.78e-8 * d, 286.5016 + 2.92961e-5 * d, 1.523688,
                        0.093405 + 2.516e-9 * d, 18.6021 + 0.5240207766 * d);
            case JUPITER:
                return orbit(100.4542 + 2.76854e-5 * d, 1.3030 - 1.557e-7 * d, 273.8777 + 1.64505e-5 * d, 5.20256,
                        0.048498 + 4.469e-9 * d, 19.8950 + 0.0830853001 * d);
            default:
                return orbit(113.6634 + 2.38980e-5 * d, 2.4886 - 1.081e-7 * d, 339.3939 + 2.97661e-5 * d, 9.55475,
                        0.055546 - 9.499e-9 * d, 316.9670 + 0.0334442282 * d);
        }
    }

    /** Ecliptic rectangular position from orbital elements: node, inclination, perihelion, axis, eccentricity, mean anomaly. */
    private static double[] orbit(double node, double incl, double peri, double a, double e, double mean) {
        double m = Math.toRadians(norm(mean));
        double ea = m + e * Math.sin(m) * (1 + e * Math.cos(m));
        for (int i = 0; i < 5; i++) ea -= (ea - e * Math.sin(ea) - m) / (1 - e * Math.cos(ea));
        double xv = a * (Math.cos(ea) - e), yv = a * Math.sqrt(1 - e * e) * Math.sin(ea);
        double v = Math.atan2(yv, xv), r = Math.sqrt(xv * xv + yv * yv);
        double n = Math.toRadians(norm(node)), i = Math.toRadians(incl), vw = v + Math.toRadians(norm(peri));
        return new double[] {
            r * (Math.cos(n) * Math.cos(vw) - Math.sin(n) * Math.sin(vw) * Math.cos(i)),
            r * (Math.sin(n) * Math.cos(vw) + Math.cos(n) * Math.sin(vw) * Math.cos(i)),
            r * Math.sin(vw) * Math.sin(i)
        };
    }

    /**
     * The bright star catalogue from the app's assets (the Yale Bright Star Catalogue, brightest
     * first): right ascension, declination, magnitude and B-V colour index for each star.
     */
    static float[] catalog(java.io.InputStream in) throws java.io.IOException {
        java.io.DataInputStream data = new java.io.DataInputStream(new java.io.BufferedInputStream(in));
        java.util.ArrayList<Float> out = new java.util.ArrayList<>();
        try {
            while (true) {
                int ra = data.readUnsignedShort();
                short dec = data.readShort(), mag = data.readShort();
                int bv = data.readUnsignedShort();
                out.add(ra / 65535f * 360f);
                out.add(dec / 32767f * 90f);
                out.add(mag / 100f);
                out.add((bv - 500) / 1000f);
            }
        } catch (java.io.EOFException end) {
            float[] stars = new float[out.size()];
            for (int i = 0; i < stars.length; i++) stars[i] = out.get(i);
            return stars;
        } finally {
            data.close();
        }
    }

    static double norm(double deg) {
        double r = deg % 360;
        return r < 0 ? r + 360 : r;
    }
}
