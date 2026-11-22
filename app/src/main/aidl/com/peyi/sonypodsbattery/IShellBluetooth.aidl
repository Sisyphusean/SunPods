package com.peyi.sonypodsbattery;

/** Pairing steps that need the shell user's Bluetooth access; served through Shizuku. */
interface IShellBluetooth {
    void destroy() = 16777114;

    /** Removes the pairing with this address. Returns a short report for diagnostics. */
    String removeBond(String address) = 1;

    /** Pairs this address over LE (Bluetooth Low Energy). */
    String bondLe(String address) = 2;

    /** Pairs this address over Classic Bluetooth. */
    String bondClassic(String address) = 3;

    /** 10 = not paired, 11 = pairing, 12 = paired. */
    int bondState(String address) = 4;

    /** Allows LE Audio for this device and connects it. */
    String connectLe(String address) = 5;

    /** Paired devices, one per line: name|address|type. */
    String bonded() = 6;

    /** Scans until this address advertises (for example, earbuds in pairing mode). False on timeout. */
    boolean waitForAdvert(String address, long timeoutMs) = 7;

    /** Allows media and calls over Classic for this device and connects them. */
    String connectClassic(String address) = 8;

    /** Turns LE Audio off for this device and disconnects it, so it falls back to Classic. */
    String dropLe(String address) = 9;
}
