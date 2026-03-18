package com.saunhardy.bluepac;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue FILL_OPACITY = BUILDER
            .comment("Fill opacity of claim markers (0.0 = transparent, 1.0 = opaque)")
            .defineInRange("fillOpacity", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LINE_OPACITY = BUILDER
            .comment("Line opacity of claim markers (0.0 = transparent, 1.0 = opaque)")
            .defineInRange("lineOpacity", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.IntValue LINE_WIDTH = BUILDER
            .comment("Line width of claim markers in pixels")
            .defineInRange("lineWidth", 2, 1, 10);

    public static final ModConfigSpec.IntValue MARKER_Y_HEIGHT = BUILDER
            .comment("Y height at which the flat claim markers are drawn on the map")
            .defineInRange("markerYHeight", 64, -64, 320);

    static final ModConfigSpec SPEC = BUILDER.build();
}
