package com.amazon.tv.leanbacklauncher.capabilities;

public abstract class LauncherConfiguration {
    private static LauncherConfiguration sInstance;

    public abstract boolean isCardElevationEnabled();

    public abstract boolean isRoundCornersEnabled();

    public static LauncherConfiguration getInstance() {
        return sInstance;
    }

    public static void setInstance(LauncherConfiguration capabilities) {
        sInstance = capabilities;
    }
}
