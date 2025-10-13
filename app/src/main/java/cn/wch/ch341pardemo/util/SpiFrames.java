package cn.wch.ch341pardemo.util;

import java.io.ByteArrayOutputStream;

/**
 * SPI 帧构造模板（增强版 v2）
 *
 * 目标：
 * 1) 把器件级参数（频率/相位/电位）→ 稳定、安全的 SPI 字节帧；
 * 2) 统一做 &0xFF、范围钳制、字序处理（AD9833 按 16 位大端，MSB first）；
 * 3) 便于在上层直接把返回的 byte[] 喂给 CH34xStreamSPI4。
 *
 * 注意：
 * - 本类只负责“帧构造”，不涉及传输/分片；如果你的底层对单次发送长度有上限，再在调用处做分片发送即可。
 */
public final class SpiFrames {

    private SpiFrames() {}

    /* **********************
     * 工具 & 基础
     * **********************/

    /** 大端写 16 位（高字节在前） */
    private static void put16(ByteArrayOutputStream bos, int word16) {
        bos.write((word16 >>> 8) & 0xFF);
        bos.write((word16      ) & 0xFF);
    }

    /** 连接多个 byte[]（忽略 null 或空数组） */
    public static byte[] sequence(byte[]... parts) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (parts != null) {
            for (byte[] p : parts) {
                if (p != null && p.length > 0) bos.write(p, 0, p.length);
            }
        }
        return bos.toByteArray();
    }

    /* **********************
     * MCP41010 数字电位器
     * **********************/

    /** 写电位值（0..255），命令字 0x11 */
    public static byte[] mcp41010Write(int value) {
        int v = Math.max(0, Math.min(255, value));
        return new byte[]{ (byte)0x11, (byte)(v & 0xFF) };
    }

    /**
     * 写电位值（归一化 0.0..1.0），内部转换为 0..255
     * @param ratio  期望电位的归一化比例，超出范围会自动钳制
     */
    public static byte[] mcp41010WriteNormalized(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) ratio = 0.0;
        double r = Math.max(0.0, Math.min(1.0, ratio));
        int value = (int)Math.round(r * 255.0);
        return mcp41010Write(value);
    }

    /* **********************
     * AD9833 直接数字合成器
     *
     * 约定：
     * - 频率字：28 位，按 LSB/MSB 拆成两帧，每帧 16 位，前缀 0x4000（FREQ0）或 0x8000（FREQ1），低 14 位为数据。
     * - 控制寄存器：常用复位/退出复位控制沿用稳定做法（0x2100 / 0x2000）。
     * - 相位字：12 位，前缀 0xC000（PHASE0）。
     * **********************/

    /** 复位（进入休眠/复位状态）：0x2100。多数应用在改频前先复位更稳妥。 */
    public static byte[] ad9833Reset() {
        return new byte[]{ (byte)0x21, 0x00 };
    }

    /** 退出复位并使用 FREQ0，正弦输出（基础配置）：0x2000。 */
    public static byte[] ad9833UseFreq0() {
        return new byte[]{ (byte)0x20, 0x00 };
    }

    /** 向 FREQ0 写入 28 位频率字（两帧 14 位，前缀 0x4000） */
    public static byte[] ad9833WriteFreq0Word(int fword28) {
        int fw = fword28 & 0x0FFFFFFF; // 28 位
        int low14  = 0x4000 | (fw        & 0x3FFF);
        int high14 = 0x4000 | ((fw >>14) & 0x3FFF);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        put16(bos, low14);
        put16(bos, high14);
        return bos.toByteArray();
    }

    /** 向 FREQ1 写入 28 位频率字（两帧 14 位，前缀 0x8000）——仅写寄存器，不切换使能通道 */
    public static byte[] ad9833WriteFreq1Word(int fword28) {
        int fw = fword28 & 0x0FFFFFFF; // 28 位
        int low14  = 0x8000 | (fw        & 0x3FFF);
        int high14 = 0x8000 | ((fw >>14) & 0x3FFF);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        put16(bos, low14);
        put16(bos, high14);
        return bos.toByteArray();
    }

    /** 写 PHASE0（12 位，0..4095），前缀 0xC000 */
    public static byte[] ad9833WritePhase0(int phase12) {
        int ph = Math.max(0, Math.min(0x0FFF, phase12));
        int word = 0xC000 | ph;
        return new byte[]{ (byte)((word >>> 8) & 0xFF), (byte)(word & 0xFF) };
    }

    /**
     * 将频率（Hz）映射为 AD9833 的 28 位频率字。
     * fword = round(freqHz * 2^28 / mclkHz)，再钳制到 [0, 2^28 - 1]。
     */
    public static int ad9833FreqToWord28(double freqHz, double mclkHz) {
        if (!(mclkHz > 0)) throw new IllegalArgumentException("mclkHz must > 0");
        long fword = Math.round(freqHz * (1L<<28) / mclkHz);
        if (fword < 0) fword = 0;
        if (fword > ((1L<<28) - 1)) fword = ((1L<<28) - 1);
        return (int) fword;
    }

    /**
     * 以频率（Hz）与系统时钟（Hz）生成完整配置序列：复位 -> 写 FREQ0 -> 退出复位
     * 这是最常用、最稳妥的改频流程。
     */
    public static byte[] ad9833ProgramFreq0(double freqHz, double mclkHz) {
        int fword28 = ad9833FreqToWord28(freqHz, mclkHz);
        byte[] reset   = ad9833Reset();
        byte[] freq0   = ad9833WriteFreq0Word(fword28);
        byte[] runFreq = ad9833UseFreq0();
        return sequence(reset, freq0, runFreq);
    }

    /**
     * 和上一个类似，但可选择是否包含“复位帧”（某些场景不希望复位器件）
     * @param withReset true=包含复位；false=仅写频率并退出复位
     */
    public static byte[] ad9833ProgramFreq0WithReset(double freqHz, double mclkHz, boolean withReset) {
        int fword28 = ad9833FreqToWord28(freqHz, mclkHz);
        byte[] freq0   = ad9833WriteFreq0Word(fword28);
        byte[] runFreq = ad9833UseFreq0();
        return withReset ? sequence(ad9833Reset(), freq0, runFreq)
                : sequence(freq0, runFreq);
    }
}
