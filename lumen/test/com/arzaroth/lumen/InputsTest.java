package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InputsTest {
    private static TvInputInfo input(String id, String label, String custom, boolean passthrough, boolean hidden, String parent) {
        TvInputInfo info = mock(TvInputInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.isPassthroughInput()).thenReturn(passthrough);
        when(info.isHidden(any())).thenReturn(hidden);
        when(info.getParentId()).thenReturn(parent);
        when(info.loadLabel(any())).thenReturn(label);
        when(info.loadCustomLabel(any())).thenReturn(custom);
        return info;
    }

    @Test
    public void passthroughInputsSortedByTheirShownName() {
        java.util.List<TvInputInfo> inputs = Arrays.asList(
                input("hw/2", "HDMI2", null, true, false, null),
                input("hw/1", "HDMI1", "Console", true, false, null),
                input("hw/9", "HDMI9", null, true, true, null),
                input("tuner", "Antenna", null, false, false, null),
                input("hw/2/cec", "CEC device", null, true, false, "hw/2"));
        TvInputManager tim = mock(TvInputManager.class);
        when(tim.getTvInputList()).thenReturn(inputs);
        Context c = new ContextWrapper(Robo.context()) {
            @Override
            public Object getSystemService(String name) {
                return Context.TV_INPUT_SERVICE.equals(name) ? tim : super.getSystemService(name);
            }
        };
        ArrayList<String> launched = new ArrayList<>();
        Menu.Page page = Inputs.page(c, i -> launched.add(i.getData().toString()));
        assertEquals(2, page.rows.size());
        assertEquals("Console", page.rows.get(0).label);
        assertEquals("HDMI2", page.rows.get(1).label);
        page.rows.get(1).action.run();
        assertEquals(1, launched.size());
    }
}
