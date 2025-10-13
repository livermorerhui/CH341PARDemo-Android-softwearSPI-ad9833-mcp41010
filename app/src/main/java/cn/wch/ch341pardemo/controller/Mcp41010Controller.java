package cn.wch.ch341pardemo.controller;

import android.hardware.usb.UsbDevice;

import cn.wch.ch341lib.CH341Manager;
import cn.wch.ch341lib.exception.CH341LibException;

/**
 * 使用 GPIO bit-bang 控制 MCP41010 数字电位器。
 * SPI 模式：MODE0（CPOL=0，CPHA=0），每次写入 16 位（命令 8 位 + 数据 8 位）。
 * 默认片选使用 D1（CS1）。
 */
public class Mcp41010Controller {

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
    private static final int CPOL = 0;
    private static final int CPHA = 0;

    private static final byte CMD_WRITE_POT0 = 0x11;

    private final CH341Manager manager = CH341Manager.getInstance();

    private UsbDevice usbDevice;
    private byte activeCsMask = GPIO_CS1;

    public void attachDevice(UsbDevice device) throws CH341LibException {
        this.usbDevice = device;
        configureIdleState();
    }

    public void detach() {
        usbDevice = null;
    }

    public void setCsChannel(int index) {
        switch (index) {
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
                activeCsMask = GPIO_CS1;
        }
    }

    public void writeValue(int value) throws CH341LibException {
        ensureDevice();
        if (value < 0) value = 0;
        if (value > 255) value = 255;
        int word = ((CMD_WRITE_POT0 & 0xFF) << 8) | (value & 0xFF);
        spiWriteWord(word, activeCsMask);
    }

    private void configureIdleState() throws CH341LibException {
        ensureDevice();
        byte idleState = GPIO_ALL_CS; // CPOL=0 -> SCK 低电平
        if (!manager.CH34xSetOutput(usbDevice, GPIO_ENABLE_MASK, GPIO_DIR_MASK, idleState & GPIO_DIR_MASK)) {
            throw new CH341LibException("CH34xSetOutput failed");
        }
        delayMicroseconds(1000);
    }

    private void spiWriteWord(int word, byte csMask) throws CH341LibException {
        byte idleClock = (byte) (CPOL == 1 ? GPIO_SCK : 0x00);
        byte idleState = (byte) (GPIO_ALL_CS | idleClock);
        byte activeState = (byte) ((GPIO_ALL_CS & ~csMask) | idleClock);

        driveLines((byte) (activeState & ~GPIO_MOSI));
        delayMicroseconds(2);

        for (int i = 0; i < 16; i++) {
            int bitIndex = MSB_FIRST ? (15 - i) : i;
            byte bitMask = (((word >> bitIndex) & 0x1) == 1) ? GPIO_MOSI : 0x00;
            byte dataState = (byte) ((activeState & ~GPIO_MOSI) | bitMask);

            if (CPHA == 0) {
                driveLines(dataState);              // 时钟低，数据准备
                delayMicroseconds(2);
                driveLines((byte) (dataState | GPIO_SCK)); // 上升沿采样
                delayMicroseconds(2);
                driveLines(dataState);              // 回到低电平
                delayMicroseconds(2);
            } else {
                driveLines((byte) (dataState | GPIO_SCK));
                delayMicroseconds(2);
                driveLines(dataState);
                delayMicroseconds(2);
            }
        }

        driveLines(idleState);
        delayMicroseconds(2);
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
            throw new CH341LibException("MCP41010 未连接 CH341 设备");
        }
    }
}
