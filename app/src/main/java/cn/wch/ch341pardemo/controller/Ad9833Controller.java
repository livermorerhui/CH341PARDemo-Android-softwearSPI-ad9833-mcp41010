package cn.wch.ch341pardemo.controller;

import android.hardware.usb.UsbDevice;
import android.os.SystemClock;

import cn.wch.ch341lib.CH341Manager;
import cn.wch.ch341lib.exception.CH341LibException;

/**
 * 控制 AD9833 的辅助类，仿照 Windows 版 pych341 实现，使用 GPIO bit-bang 发送 16bit 指令。
 *
 * 默认使用 CH341 的 D0 作为 CS，D3 作为 SCLK，D5 作为 MOSI。
 * SPI 模式采用 MODE2（CPOL=1，CPHA=0），与 AD9833 数据手册相符。
 */
public class Ad9833Controller {

    private static final int DEFAULT_MCLK = 25_000_000;

    // 控制寄存器位定义
    private static final int AD_B28 = 13;
    private static final int AD_FSELECT = 11;
    private static final int AD_PSELECT = 10;
    private static final int AD_RESET = 8;
    private static final int AD_SLEEP1 = 7;
    private static final int AD_SLEEP12 = 6;
    private static final int AD_OPBITEN = 5;
    private static final int AD_DIV2 = 3;
    private static final int AD_MODE = 1;

    private static final int AD_FREQ0 = 14;
    private static final int AD_FREQ1 = 15;

    public static final int MODE_BITS_OFF = (1 << AD_SLEEP1) | (1 << AD_SLEEP12);
    public static final int MODE_BITS_TRIANGLE = (1 << AD_MODE);
    public static final int MODE_BITS_SQUARE2 = (1 << AD_OPBITEN);
    public static final int MODE_BITS_SQUARE1 = (1 << AD_OPBITEN) | (1 << AD_DIV2);
    public static final int MODE_BITS_SINE = 0;

    private static final int MODE_CLEAR_MASK = ~(MODE_BITS_OFF | MODE_BITS_TRIANGLE | MODE_BITS_SQUARE2 | MODE_BITS_SQUARE1);

    public static final int CHANNEL_0 = 0;
    public static final int CHANNEL_1 = 1;

    // GPIO bit masks (D0-D5)
    private static final int GPIO_ENABLE_MASK = 0x3F;
    private static final int GPIO_DIR_MASK = 0x0000002F;
    private static final byte GPIO_CS0 = 0x01;
    private static final byte GPIO_CS1 = 0x02;
    private static final byte GPIO_CS2 = 0x04;
    private static final byte GPIO_SCK = 0x08;
    private static final byte GPIO_MOSI = 0x20;
    private static final byte GPIO_DIR = (byte) (GPIO_CS0 | GPIO_CS1 | GPIO_CS2 | GPIO_SCK | GPIO_MOSI);
    private static final byte GPIO_ALL_CS = (byte) (GPIO_CS0 | GPIO_CS1 | GPIO_CS2);

    private static final boolean MSB_FIRST = true;
    private static final int CPOL = 1;
    private static final int CPHA = 0;

    private final CH341Manager manager = CH341Manager.getInstance();

    private UsbDevice usbDevice;
    private int mclkHz = DEFAULT_MCLK;
    private int controlRegister = 0;
    private byte activeCsMask = GPIO_CS0;

    public void attachDevice(UsbDevice device) throws CH341LibException {
        this.usbDevice = device;
        configureIdleState();
    }

    public void detach() {
        this.usbDevice = null;
        this.controlRegister = 0;
    }

    public void begin() throws CH341LibException {
        ensureDevice();
        controlRegister = (1 << AD_B28);
        writeWord(controlRegister);
        reset();
    }

    public void reset() throws CH341LibException {
        ensureDevice();
        controlRegister |= (1 << AD_RESET);
        writeWord(controlRegister);
        delayMicroseconds(5000);
        controlRegister &= ~(1 << AD_RESET);
        writeWord(controlRegister);
    }

    public void setMode(int modeBits) throws CH341LibException {
        ensureDevice();
        controlRegister &= MODE_CLEAR_MASK;
        controlRegister |= modeBits;
        writeWord(controlRegister);
    }

    public void setFrequency(int channel, double frequencyHz) throws CH341LibException {
        ensureDevice();
        if (channel != CHANNEL_0 && channel != CHANNEL_1) {
            throw new IllegalArgumentException("channel must be 0 or 1");
        }
        if (frequencyHz < 0) {
            throw new IllegalArgumentException("frequency must be >= 0");
        }
        long freqReg = Math.round(frequencyHz * ((1L << 28) / (double) mclkHz));
        int addrMask = (channel == CHANNEL_0) ? (1 << AD_FREQ0) : (1 << AD_FREQ1);

        int lsbWord = addrMask | (int) (freqReg & 0x3FFF);
        int msbWord = addrMask | (int) ((freqReg >> 14) & 0x3FFF);

        writeWord(controlRegister);
        writeWord(lsbWord);
        writeWord(msbWord);
    }

    public void setActiveFrequency(int channel) throws CH341LibException {
        ensureDevice();
        if (channel == CHANNEL_0) {
            controlRegister &= ~(1 << AD_FSELECT);
        } else if (channel == CHANNEL_1) {
            controlRegister |= (1 << AD_FSELECT);
        } else {
            throw new IllegalArgumentException("channel must be 0 or 1");
        }
        writeWord(controlRegister);
    }

    public void shutdown() throws CH341LibException {
        ensureDevice();
        setMode(MODE_BITS_OFF);
    }

    public void setCsChannel(int channelIndex) {
        switch (channelIndex) {
            case 0:
                activeCsMask = GPIO_CS0;
                break;
            case 1:
                activeCsMask = GPIO_CS1;
                break;
            case 2:
                activeCsMask = GPIO_CS2;
                break;
            default:
                activeCsMask = GPIO_CS0;
        }
    }

    private void configureIdleState() throws CH341LibException {
        ensureDevice();
        byte idleState = (byte) (GPIO_ALL_CS | GPIO_SCK); // CPOL=1 -> SCK 高电平
        if (!manager.CH34xSetOutput(usbDevice, GPIO_ENABLE_MASK, GPIO_DIR_MASK, idleState & GPIO_DIR_MASK)) {
            throw new CH341LibException("CH34xSetOutput failed");
        }
        delayMicroseconds(1000);
    }

    private void writeWord(int word) throws CH341LibException {
        byte[] payload = new byte[]{
                (byte) ((word >> 8) & 0xFF),
                (byte) (word & 0xFF)
        };
        spiWrite(payload, activeCsMask);
        delayMicroseconds(10);
    }

    private void spiWrite(byte[] payload, byte csMask) throws CH341LibException {
        if (payload == null || payload.length == 0) {
            return;
        }
        byte idleClock = (byte) (CPOL == 1 ? GPIO_SCK : 0x00);
        byte idleState = (byte) (GPIO_ALL_CS | idleClock);
        byte activeIdleState = (byte) ((GPIO_ALL_CS & ~csMask) | idleClock);

        for (int i = 0; i < payload.length; i += 2) {
            int high = payload[i] & 0xFF;
            int low = (i + 1 < payload.length) ? (payload[i + 1] & 0xFF) : 0;
            int word = (high << 8) | low;
            driveLines((byte) (activeIdleState & ~GPIO_MOSI));
            delayMicroseconds(2);
            transferWord(word, activeIdleState);
            driveLines((byte) (idleState & ~GPIO_MOSI));
            delayMicroseconds(2);
        }
    }

    private void transferWord(int word, byte activeIdleState) throws CH341LibException {
        for (int bit = 0; bit < 16; bit++) {
            int bitIndex = MSB_FIRST ? (15 - bit) : bit;
            byte bitMask = (((word >> bitIndex) & 0x1) == 1) ? GPIO_MOSI : 0x00;

            if (CPHA == 0) {
                byte dataState = (byte) ((activeIdleState & ~GPIO_MOSI) | bitMask);
                driveLines(dataState);
                delayMicroseconds(2);
                driveLines((byte) (dataState ^ GPIO_SCK));
                delayMicroseconds(2);
                driveLines(dataState);
                delayMicroseconds(2);
            } else {
                byte firstEdge = (byte) ((activeIdleState & ~GPIO_MOSI));
                driveLines((byte) (firstEdge ^ GPIO_SCK));
                delayMicroseconds(2);
                byte dataState = (byte) ((activeIdleState & ~GPIO_MOSI) | bitMask);
                driveLines((byte) (dataState ^ GPIO_SCK));
                delayMicroseconds(2);
                driveLines(dataState);
                delayMicroseconds(2);
            }
        }
    }

    private void driveLines(byte state) throws CH341LibException {
        ensureDevice();
        if (!manager.CH34xSet_D5_D0(usbDevice, GPIO_DIR, state)) {
            throw new CH341LibException("CH34xSet_D5_D0 failed");
        }
    }

    private void delayMicroseconds(long micros) {
        if (micros <= 0) {
            return;
        }
        long millis = micros / 1000;
        int nanos = (int) ((micros % 1000) * 1000);
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureDevice() throws CH341LibException {
        if (usbDevice == null) {
            throw new CH341LibException("AD9833 未连接 CH341 设备");
        }
    }
}
