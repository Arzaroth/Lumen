package com.arzaroth.lumen;

import org.json.JSONException;

/** The volcano painted in code by `VolcanoPainter`, brought to life by the same layers as the Volcano pack. */
final class DrawnVolcano {
    private static final String JSON = "{\"id\":\"volcano-drawn\",\"name\":\"Volcano (drawn)\",\"image\":\"painted\","
            + "\"anchor\":{\"image\":[" + VolcanoPainter.VENT_X + "," + VolcanoPainter.VENT_Y + "],\"screen\":[" + VolcanoPainter.VENT_X + "," + VolcanoPainter.VENT_Y + "]},"
            + "\"palette\":{\"hot\":\"#FFF1C2\",\"warm\":\"#FF9A3C\",\"dust\":[\"#FFE6C0\",\"#FF8A3D\"],"
            + "\"motes\":[\"#FFF1C2\",\"#FFC27A\",\"#FF8A3D\",\"#FF5A1F\"],\"glow\":\"#FF7A2A\",\"scrim\":\"#0C0504\",\"calm\":\"#FFB27A\"},"
            + "\"layers\":[{\"type\":\"stars\",\"above\":0.7,\"count\":160},"
            + "{\"type\":\"sparks\",\"from\":[" + VolcanoPainter.VENT_X + "," + (VolcanoPainter.VENT_Y - 0.01f) + "],\"width\":0.02,\"rate\":22,"
            + "\"speed\":[0.08,0.2],\"angle\":[-115,-65],\"gravity\":0.1,\"life\":[1.0,2.4],\"size\":[1.0,2.2],"
            + "\"colors\":[\"#FFF0B0\",\"#FFA040\",\"#FF4A10\"]}]}";

    static final Theme THEME = theme();

    private DrawnVolcano() {
    }

    private static Theme theme() {
        try {
            return new PackTheme(Pack.painted(JSON, VolcanoPainter::paint));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
