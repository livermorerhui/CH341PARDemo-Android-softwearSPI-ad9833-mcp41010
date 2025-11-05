package cn.wch.ch341pardemo;

import static cn.wch.ch341pardemo.global.Global.CLOSE_DEVICE;
import static cn.wch.ch341pardemo.global.Global.OPEN_DEVICE;
import static cn.wch.ch341pardemo.global.Global.STREAM_CONFIG_SUCCESS;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;

import java.text.DecimalFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.wch.ch341lib.CH341Manager;
import cn.wch.ch341lib.exception.CH341LibException;
import cn.wch.ch341pardemo.controller.Ad9833Controller;
import cn.wch.ch341pardemo.controller.Mcp41010Controller;
import cn.wch.ch341pardemo.databinding.ActivityMainBinding;
import cn.wch.ch341pardemo.global.Global;
import cn.wch.ch341pardemo.util.FormatUtil;
import cn.wch.ch341pardemo.util.LogUtil;
import cn.wch.ch347lib.base.i2c.EEPROM_TYPE;
import cn.wch.ch347lib.callback.IUsbStateChange;
import cn.wch.ch347lib.exception.ChipException;
import cn.wch.ch347lib.exception.NoPermissionException;

public class MainActivity extends AppCompatActivity {

    private  Context context;
    private ActivityMainBinding binding;
    private UsbDevice usbDevice; //当前打开的USB设备
    private boolean isDeviceOpen = false;//设备是否打开
    private final ExecutorService ioExec = Executors.newSingleThreadExecutor();
    private Ad9833Controller ad9833Controller;
    private Mcp41010Controller mcpController;
    private int mcpCurrentValue = 0;
    private static final int AD9833_MCLK_HZ = Ad9833Controller.DEFAULT_MCLK_HZ;
    private final DecimalFormat freqFormatter = createDecimalFormat("0.######");
    private final DecimalFormat phaseFormatter = createDecimalFormat("0.###");
    private final TextWatcher ad9833FreqWatcher = new SimpleTextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateAd9833FrequencyPreview();
        }
    };
    private final TextWatcher ad9833PhaseWatcher = new SimpleTextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateAd9833PhasePreview();
        }
    };
    private final TextWatcher waveformInputWatcher = new SimpleTextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateWaveformPreview(false);
        }
    };
    private final TextWatcher frequencyInputWatcher = new SimpleTextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateFrequencyPreview(false);
        }
    };
    private final TextWatcher amplitudeInputWatcher = new SimpleTextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateAmplitudePreview(false);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        context = this;
        setContentView(binding.getRoot());

        initView();
        initVariable();
        setViewClickListener();

    }

    private void initView() {
        initSpinner(binding.streamItem.streamWorkRateSpinner,R.array.work_rate);
        initSpinner(binding.streamItem.spiWorkModeSpinner,R.array.spi_work_mode);
        initSpinner(binding.streamItem.spiDataModeSpinner,R.array.spi_data_mode);
        initSpinner(binding.streamItem.spi.spiChipSelectSpinner,R.array.spi_chip_select);
        initSpinner(binding.streamItem.eeprom.eepromTypeSpinner,R.array.eeprom_type);

    }

    private void initVariable() {
        CH341Manager.getInstance().setUsbStateListener(iUsbStateChange);
        setBtnEnable(false);
        setAd9833ControlsEnabled(false);
        setMcpControlsEnabled(false);
        setSpiSequenceControlsEnabled(false);
        ad9833Controller = new Ad9833Controller();
        mcpController = new Mcp41010Controller();
        binding.mcp41010Item.mcp41010ValueSeek.setMax(255);
        binding.mcp41010Item.mcp41010ValueSeek.setProgress(mcpCurrentValue);
        binding.ad9833Item.ad9833FreqEdit.addTextChangedListener(ad9833FreqWatcher);
        binding.ad9833Item.ad9833PhaseEdit.addTextChangedListener(ad9833PhaseWatcher);
        binding.ad9833Item.ad9833PhaseEdit.setText("0");
        binding.spiSequenceItem.spiSequenceDefaultDelayEdit.setText("5");
        binding.spiSequenceItem.spiSequenceLogText.setText("--");
        binding.spiSequenceItem.spiWaveformDelayEdit.addTextChangedListener(waveformInputWatcher);
        binding.spiSequenceItem.spiFrequencyValueEdit.addTextChangedListener(frequencyInputWatcher);
        binding.spiSequenceItem.spiFrequencyDelayEdit.addTextChangedListener(frequencyInputWatcher);
        binding.spiSequenceItem.spiAmplitudeValueEdit.addTextChangedListener(amplitudeInputWatcher);
        binding.spiSequenceItem.spiAmplitudeDelayEdit.addTextChangedListener(amplitudeInputWatcher);
        binding.spiSequenceItem.spiWaveformRadioGroup.check(R.id.spi_waveform_sine_option);
        binding.spiSequenceItem.spiFrequencyValueEdit.setText("0");
        binding.spiSequenceItem.spiAmplitudeValueEdit.setText(String.valueOf(mcpCurrentValue));
        updateAd9833StaticCommandLabels();
        updateAd9833FrequencyPreview();
        updateAd9833PhasePreview();
        updateMcpValueLabel(mcpCurrentValue);
        updateWaveformPreview(false);
        updateFrequencyPreview(false);
        updateAmplitudePreview(false);
//        setSPIBtnEnable(false,false);
    }

    private void setViewClickListener() {
        //使能设备
        binding.btnOpen.setOnClickListener(view -> {
            if(!isDeviceOpen){
                openDevice();
            }else {
                closeDevice();
            }
        });
        //EPP并口数据读
        binding.eppItem.eppReadDataBtn.setOnClickListener(view -> {
            handleEppReadData();

        });
        //EPP并口数据写
        binding.eppItem.eppWriteDataBtn.setOnClickListener(view -> {
            handleEppWriteData();

        });
        //EPP并口读写数据清空
        binding.eppItem.eppRwDataClearText.setOnClickListener(view -> {
            binding.eppItem.eppRwDataEdit.setText("");
        });

        //EPP并口地址读
        binding.eppItem.eppReadAddrBtn.setOnClickListener(view -> {
            handleEppReadAddr();

        });
        //EPP并口地址写
        binding.eppItem.eppWriteAddrBtn.setOnClickListener(view -> {
            handleEppWriteAddr();

        });
        //EPP并口读写数据清空
        binding.eppItem.eppRwAddrClearText.setOnClickListener(view -> {
            binding.eppItem.eppRwAddrEdit.setText("");
        });
        //MEM并口数据读
        binding.memItem.memReadDataBtn.setOnClickListener(view -> {
            handleMemReadData();

        });
        //MEM并口数据写
        binding.memItem.memWriteDataBtn.setOnClickListener(view -> {
            handleMemWriteData();

        });
        //MEM并口读写数据清空
        binding.memItem.memRwDataClearText.setOnClickListener(view -> {
            binding.memItem.memRwDataEdit.setText("");
        });
        //两线串口设置
        binding.streamItem.streamModeConfigBtn.setOnClickListener(view -> {
            handleStreamConfig();
        });
        //SPI读写
        binding.streamItem.spi.spiRwBtn.setOnClickListener(view -> {
            handleSPIRW();
        });
        //SPI写清空
        binding.streamItem.spi.spiWriteDataClearText.setOnClickListener(view -> {
            binding.streamItem.spi.spiWriteDataEdit.setText("");
        });
        //SPI读清空
        binding.streamItem.spi.spiReadDataClearText.setOnClickListener(view -> {
            binding.streamItem.spi.spiReadDataEdit.setText("");
        });
        //I2C读写
        binding.streamItem.i2c.i2cRwBtn.setOnClickListener(view -> {
            handleI2CRW();
        });
        //I2C读清空
        binding.streamItem.i2c.i2cReadDataClearText.setOnClickListener(view -> {
            binding.streamItem.i2c.i2cReadDataEdit.setText("");
        });
        //I2C写清空
        binding.streamItem.i2c.i2cWriteDataClearText.setOnClickListener(view -> {
            binding.streamItem.i2c.i2cWriteDataEdit.setText("");
        });
        //EEPROM写
        binding.streamItem.eeprom.eepromWriteBtn.setOnClickListener(view -> {
            handelEEPROMWrite();
        });
        //EEPROM读
        binding.streamItem.eeprom.eepromReadBtn.setOnClickListener(view -> {
            handelEEPROMRead();
        });
        //EEPROM 读数据清空
        binding.streamItem.eeprom.eepromReadDataClearText.setOnClickListener(view -> {
            binding.streamItem.eeprom.eepromReadDataEdit.setText("");
        });
        //EEPROM 写清空
        binding.streamItem.eeprom.eepromWriteDataClearText.setOnClickListener(view -> {
            binding.streamItem.eeprom.eepromWriteDataEdit.setText("");
        });
        //读取GPIO
        binding.gpioItem.readGpioBtn.setOnClickListener(view -> {
            handelReadGpio();
        });
        binding.gpioItem.setGpioBtn.setOnClickListener(view -> {
            handleSetGpio();
        });

        binding.ad9833Item.ad9833SetFreqBtn.setOnClickListener(view -> handleAd9833SetFrequency());
        binding.ad9833Item.ad9833ResetSendBtn.setOnClickListener(view -> handleAd9833Reset());
        binding.ad9833Item.ad9833SineSendBtn.setOnClickListener(view -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_SINE, "正弦波"));
        binding.ad9833Item.ad9833TriangleSendBtn.setOnClickListener(view -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_TRIANGLE, "三角波"));
        binding.ad9833Item.ad9833SquareSendBtn.setOnClickListener(view -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_SQUARE2, "方波"));
        binding.ad9833Item.ad9833SquareHalfSendBtn.setOnClickListener(view -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_SQUARE1, "方波/2"));
        binding.ad9833Item.ad9833MuteSendBtn.setOnClickListener(view -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_OFF, "关闭输出"));

        binding.ad9833Item.ad9833PhaseSendBtn.setOnClickListener(view -> handleAd9833SetPhase());
        binding.spiSequenceItem.spiSequenceClearBtn.setOnClickListener(view -> binding.spiSequenceItem.spiSequenceEdit.setText(""));
        binding.spiSequenceItem.spiSequenceSendBtn.setOnClickListener(view -> handleSpiSequenceSend());
        binding.spiSequenceItem.spiWaveformInsertBtn.setOnClickListener(view -> handleWaveformInsert());
        binding.spiSequenceItem.spiFrequencyInsertBtn.setOnClickListener(view -> handleFrequencyInsert());
        binding.spiSequenceItem.spiAmplitudeInsertBtn.setOnClickListener(view -> handleAmplitudeInsert());
        binding.spiSequenceItem.spiWaveformRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateWaveformPreview(false);
            updateFrequencyPreview(false);
        });

        binding.mcp41010Item.mcp41010ValueSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mcpCurrentValue = progress;
                updateMcpValueLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        binding.mcp41010Item.mcp41010SetBtn.setOnClickListener(view -> handleMcpSetValue());
    }



    /*****************************************UI***************************************************/

    //使能按钮
    private void setBtnEnable(boolean enable){
        binding.eppItem.eppReadDataBtn.setEnabled(enable);
        binding.eppItem.eppWriteDataBtn.setEnabled(enable);
        binding.eppItem.eppReadAddrBtn.setEnabled(enable);
        binding.eppItem.eppWriteAddrBtn.setEnabled(enable);
        binding.memItem.memReadDataBtn.setEnabled(enable);
        binding.memItem.memWriteDataBtn.setEnabled(enable);
    }
    //使能同步串口按钮
    private void setStreamBtnEnable(boolean enable, boolean isInit){
        binding.streamItem.streamModeConfigBtn.setEnabled(enable);
        binding.streamItem.spi.spiRwBtn.setEnabled(isInit);
        binding.streamItem.i2c.i2cRwBtn.setEnabled(isInit);
        binding.streamItem.eeprom.eepromReadBtn.setEnabled(isInit);
        binding.streamItem.eeprom.eepromWriteBtn.setEnabled(isInit);
    }

    private void setAd9833ControlsEnabled(boolean enable) {
        binding.ad9833Item.ad9833SetFreqBtn.setEnabled(enable);
        binding.ad9833Item.ad9833ResetSendBtn.setEnabled(enable);
        binding.ad9833Item.ad9833SineSendBtn.setEnabled(enable);
        binding.ad9833Item.ad9833TriangleSendBtn.setEnabled(enable);
        binding.ad9833Item.ad9833SquareSendBtn.setEnabled(enable);
        binding.ad9833Item.ad9833SquareHalfSendBtn.setEnabled(enable);
        binding.ad9833Item.ad9833MuteSendBtn.setEnabled(enable);

        binding.ad9833Item.ad9833PhaseSendBtn.setEnabled(enable);
    }

    private void setMcpControlsEnabled(boolean enable) {
        binding.mcp41010Item.mcp41010ValueSeek.setEnabled(enable);
        binding.mcp41010Item.mcp41010SetBtn.setEnabled(enable);
    }

    private void setSpiSequenceControlsEnabled(boolean enable) {
        binding.spiSequenceItem.spiSequenceSendBtn.setEnabled(enable);
    }
    private void handleAd9833SetFrequency() {
        if (!isDeviceOpen || usbDevice == null) {
            showToast("请先打开设备");
            return;
        }
        String freqStr = binding.ad9833Item.ad9833FreqEdit.getText().toString().trim();
        if (freqStr.isEmpty()) {
            showToast("频率不能为空");
            return;
        }
        double freq;
        try {
            freq = Double.parseDouble(freqStr);
        } catch (NumberFormatException e) {
            showToast("频率格式不正确");
            return;
        }
        final double finalFreq = freq;
        ioExec.execute(() -> {
            try {
                ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, finalFreq);
                ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0);
                runOnUiThread(() -> showToast("频率已设置为 " + finalFreq + " Hz"));
            } catch (IllegalArgumentException | CH341LibException e) {
                runOnUiThread(() -> showToast("设置频率失败: " + e.getMessage()));
            }
        });
    }

    private void handleAd9833SetMode(int modeBits, String label) {
        if (!isDeviceOpen || usbDevice == null) {
            showToast("请先打开设备");
            return;
        }
        ioExec.execute(() -> {
            try {
                ad9833Controller.setMode(modeBits);
                runOnUiThread(() -> showToast("波形已切换为 " + label));
            } catch (CH341LibException e) {
                runOnUiThread(() -> showToast("设置波形失败: " + e.getMessage()));
            }
        });
    }

    private void handleAd9833Reset() {
        if (!isDeviceOpen || usbDevice == null) {
            showToast("请先打开设备");
            return;
        }
        ioExec.execute(() -> {
            try {
                ad9833Controller.reset();
                runOnUiThread(() -> showToast("已发送复位指令"));
            } catch (CH341LibException e) {
                runOnUiThread(() -> showToast("复位失败: " + e.getMessage()));
            }
        });
    }

    private void handleAd9833SetPhase() {
        if (!isDeviceOpen || usbDevice == null) {
            showToast("请先打开设备");
            return;
        }
        String phaseStr = binding.ad9833Item.ad9833PhaseEdit.getText().toString().trim();
        if (phaseStr.isEmpty()) {
            showToast("相位不能为空");
            return;
        }
        double phase;
        try {
            phase = Double.parseDouble(phaseStr);
        } catch (NumberFormatException e) {
            showToast("相位格式不正确");
            return;
        }
        final double finalPhase = phase;
        ioExec.execute(() -> {
            try {
                ad9833Controller.setPhaseDegrees(Ad9833Controller.CHANNEL_0, finalPhase);
                runOnUiThread(() -> showToast("相位指令已发送"));
            } catch (IllegalArgumentException | CH341LibException e) {
                runOnUiThread(() -> showToast("设置相位失败: " + e.getMessage()));
            }
        });
    }

    private void updateAd9833StaticCommandLabels() {
        binding.ad9833Item.ad9833ResetCmdText.setText(formatWord(Ad9833Controller.RESET_WORD));
        binding.ad9833Item.ad9833SineCmdText.setText(formatWord(Ad9833Controller.composeControlWord(Ad9833Controller.MODE_BITS_SINE)));
        binding.ad9833Item.ad9833TriangleCmdText.setText(formatWord(Ad9833Controller.composeControlWord(Ad9833Controller.MODE_BITS_TRIANGLE)));
        binding.ad9833Item.ad9833SquareCmdText.setText(formatWord(Ad9833Controller.composeControlWord(Ad9833Controller.MODE_BITS_SQUARE2)));
        binding.ad9833Item.ad9833SquareHalfCmdText.setText(formatWord(Ad9833Controller.composeControlWord(Ad9833Controller.MODE_BITS_SQUARE1)));
        binding.ad9833Item.ad9833MuteCmdText.setText(formatWord(Ad9833Controller.composeControlWord(Ad9833Controller.MODE_BITS_OFF)));
    }

    private void updateAd9833FrequencyPreview() {
        String freqStr = binding.ad9833Item.ad9833FreqEdit.getText().toString().trim();
        if (freqStr.isEmpty()) {
            binding.ad9833Item.ad9833FreqFormulaText.setText("公式：round(f × 2^28 / 25,000,000)");
            binding.ad9833Item.ad9833FreqRegisterText.setText("寄存器值：--");
            binding.ad9833Item.ad9833FreqWordsText.setText("指令内容：--");
            return;
        }
        double freq;
        try {
            freq = Double.parseDouble(freqStr);
        } catch (NumberFormatException e) {
            binding.ad9833Item.ad9833FreqFormulaText.setText("公式：输入格式错误");
            binding.ad9833Item.ad9833FreqRegisterText.setText("寄存器值：--");
            binding.ad9833Item.ad9833FreqWordsText.setText("指令内容：--");
            return;
        }
        if (freq < 0) {
            binding.ad9833Item.ad9833FreqFormulaText.setText("公式：频率必须大于等于 0");
            binding.ad9833Item.ad9833FreqRegisterText.setText("寄存器值：--");
            binding.ad9833Item.ad9833FreqWordsText.setText("指令内容：--");
            return;
        }
        int freqRegister = Ad9833Controller.computeFrequencyRegister(freq, AD9833_MCLK_HZ);
        String freqDisplay = freqFormatter.format(freq);
        binding.ad9833Item.ad9833FreqFormulaText.setText(String.format(Locale.US,
                "公式：round(%s × 2^28 / 25,000,000) = %d", freqDisplay, freqRegister));
        binding.ad9833Item.ad9833FreqRegisterText.setText(String.format(Locale.US,
                "寄存器值：%d (0x%07X)", freqRegister, freqRegister));
        int lsbWord = Ad9833Controller.buildFrequencyWord(Ad9833Controller.CHANNEL_0, false, freqRegister);
        int msbWord = Ad9833Controller.buildFrequencyWord(Ad9833Controller.CHANNEL_0, true, freqRegister);
        binding.ad9833Item.ad9833FreqWordsText.setText("指令内容：" + joinWords(lsbWord, msbWord));
    }

    private void updateAd9833PhasePreview() {
        String phaseStr = binding.ad9833Item.ad9833PhaseEdit.getText().toString().trim();
        if (phaseStr.isEmpty()) {
            binding.ad9833Item.ad9833PhaseFormulaText.setText("公式：round(相位 × 4096 / 360)");
            binding.ad9833Item.ad9833PhaseRegisterText.setText("寄存器值：--");
            binding.ad9833Item.ad9833PhaseWordsText.setText("指令内容：--");
            return;
        }
        double phase;
        try {
            phase = Double.parseDouble(phaseStr);
        } catch (NumberFormatException e) {
            binding.ad9833Item.ad9833PhaseFormulaText.setText("公式：输入格式错误");
            binding.ad9833Item.ad9833PhaseRegisterText.setText("寄存器值：--");
            binding.ad9833Item.ad9833PhaseWordsText.setText("指令内容：--");
            return;
        }
        int phaseValue;
        try {
            phaseValue = Ad9833Controller.normalizePhaseDegrees(phase);
        } catch (IllegalArgumentException e) {
            binding.ad9833Item.ad9833PhaseFormulaText.setText("公式：输入范围错误");
            binding.ad9833Item.ad9833PhaseRegisterText.setText("寄存器值：--");
            binding.ad9833Item.ad9833PhaseWordsText.setText("指令内容：--");
            return;
        }
        String phaseDisplay = phaseFormatter.format(phase);
        binding.ad9833Item.ad9833PhaseFormulaText.setText(String.format(Locale.US,
                "公式：round(%s × 4096 / 360) = %d", phaseDisplay, phaseValue));
        binding.ad9833Item.ad9833PhaseRegisterText.setText(String.format(Locale.US,
                "寄存器值：%d (0x%03X)", phaseValue, phaseValue));
        int phaseWord = Ad9833Controller.buildPhaseWord(Ad9833Controller.CHANNEL_0, phaseValue);
        binding.ad9833Item.ad9833PhaseWordsText.setText("指令内容：" + formatWord(phaseWord));
    }

    private void updateMcpInstructionDetails(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        binding.mcp41010Item.mcp41010RegisterText.setText(String.format(Locale.US,
                "数据值：%d (0x%02X)", clamped, clamped));
        int commandWord = Mcp41010Controller.buildCommandWord(clamped);
        binding.mcp41010Item.mcp41010CommandText.setText("指令内容：" + formatWord(commandWord));
        binding.mcp41010Item.mcp41010BytesText.setText(String.format(Locale.US,
                "字节序列：%02X %02X", (commandWord >> 8) & 0xFF, commandWord & 0xFF));
    }

    private void handleSpiSequenceSend() {
        if (!isDeviceOpen || usbDevice == null) {
            showToast("请先打开设备");
            return;
        }
        String rawSequence = binding.spiSequenceItem.spiSequenceEdit.getText().toString();
        String defaultDelayStr = binding.spiSequenceItem.spiSequenceDefaultDelayEdit.getText().toString().trim();
        long defaultDelayMs;
        try {
            defaultDelayMs = parseDelayMilliseconds(defaultDelayStr);
        } catch (IllegalArgumentException e) {
            showToast(e.getMessage());
            return;
        }
        List<SpiSequenceEntry> entries;
        try {
            entries = parseSpiSequence(rawSequence, defaultDelayMs);
        } catch (IllegalArgumentException e) {
            showToast(e.getMessage());
            return;
        }
        if (entries.isEmpty()) {
            showToast("请输入有效序列");
            return;
        }
        binding.spiSequenceItem.spiSequenceLogText.setText("执行中...");
        ioExec.execute(() -> executeSpiSequence(entries));
    }

    private void insertSpiSequenceSample() {
        String sample = "AD9833:2100 2000 | 10\nMCP41010:1100 | 20";
        String current = binding.spiSequenceItem.spiSequenceEdit.getText().toString();
        if (current.trim().isEmpty()) {
            binding.spiSequenceItem.spiSequenceEdit.setText(sample);
        } else {
            binding.spiSequenceItem.spiSequenceEdit.append("\n" + sample);
        }
    }

    private void handleWaveformInsert() {
        SpiCommandBuildResult result = buildWaveformCommand();
        if (!result.valid) {
            updateWaveformPreview(false);
            showToast(!TextUtils.isEmpty(result.errorMessage) ? result.errorMessage : "请完善波形参数");
            return;
        }
        appendSequenceLine(result.commandText);
        renderWaveformPreview(result, true);
    }

    private void handleFrequencyInsert() {
        SpiCommandBuildResult result = buildFrequencyCommand();
        if (!result.valid) {
            updateFrequencyPreview(false);
            showToast(!TextUtils.isEmpty(result.errorMessage) ? result.errorMessage : "请完善频率参数");
            return;
        }
        appendSequenceLine(result.commandText);
        renderFrequencyPreview(result, true);
    }

    private void handleAmplitudeInsert() {
        SpiCommandBuildResult result = buildAmplitudeCommand();
        if (!result.valid) {
            updateAmplitudePreview(false);
            showToast(!TextUtils.isEmpty(result.errorMessage) ? result.errorMessage : "请完善幅度参数");
            return;
        }
        appendSequenceLine(result.commandText);
        renderAmplitudePreview(result, true);
    }

    private void updateWaveformPreview(boolean highlightInserted) {
        renderWaveformPreview(buildWaveformCommand(), highlightInserted);
    }

    private void updateFrequencyPreview(boolean highlightInserted) {
        renderFrequencyPreview(buildFrequencyCommand(), highlightInserted);
    }

    private void updateAmplitudePreview(boolean highlightInserted) {
        renderAmplitudePreview(buildAmplitudeCommand(), highlightInserted);
    }

    private void renderWaveformPreview(SpiCommandBuildResult result, boolean highlightWhenValid) {
        renderPreview(binding.spiSequenceItem.spiWaveformPreviewText, "波形预览", result, highlightWhenValid);
    }

    private void renderFrequencyPreview(SpiCommandBuildResult result, boolean highlightWhenValid) {
        renderPreview(binding.spiSequenceItem.spiFrequencyPreviewText, "频率预览", result, highlightWhenValid);
    }

    private void renderAmplitudePreview(SpiCommandBuildResult result, boolean highlightWhenValid) {
        renderPreview(binding.spiSequenceItem.spiAmplitudePreviewText, "幅度预览", result, highlightWhenValid);
    }

    private void renderPreview(android.widget.TextView previewView, String label, SpiCommandBuildResult result, boolean highlightWhenValid) {
        if (previewView == null) {
            return;
        }
        String content = result.commandText;
        String display;
        if (TextUtils.isEmpty(content)) {
            display = label + "：--";
        } else if (result.valid) {
            display = label + "：\n" + content;
        } else {
            display = label + "：" + content;
        }
        previewView.setText(display);
        int colorRes = (highlightWhenValid && result.valid) ? R.color.title_text_color : R.color.hint_text_color;
        previewView.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private SpiCommandBuildResult buildWaveformCommand() {
        if (binding == null) {
            return new SpiCommandBuildResult(false, "", "绑定未初始化");
        }
        int modeBits = resolveSelectedWaveformModeBits();
        if (modeBits == -1) {
            String message = "请选择波形";
            return new SpiCommandBuildResult(false, message, message);
        }
        String delayStr = binding.spiSequenceItem.spiWaveformDelayEdit.getText().toString().trim();
        long delayMs = 0;
        boolean hasDelay = !delayStr.isEmpty();
        if (hasDelay) {
            try {
                delayMs = Long.parseLong(delayStr);
            } catch (NumberFormatException e) {
                String message = "延迟格式不正确";
                return new SpiCommandBuildResult(false, message, message);
            }
            if (delayMs < 0) {
                String message = "延迟不能为负值";
                return new SpiCommandBuildResult(false, message, message);
            }
        }
        int controlWord = Ad9833Controller.composeControlWord(modeBits);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "AD9833:%04X %04X", controlWord, controlWord));
        if (hasDelay) {
            builder.append(" | ").append(delayMs);
        }
        return new SpiCommandBuildResult(true, builder.toString(), null);
    }

    private SpiCommandBuildResult buildFrequencyCommand() {
        if (binding == null) {
            return new SpiCommandBuildResult(false, "", "绑定未初始化");
        }
        String freqStr = binding.spiSequenceItem.spiFrequencyValueEdit.getText().toString().trim();
        if (freqStr.isEmpty()) {
            String message = "请输入频率";
            return new SpiCommandBuildResult(false, message, message);
        }
        double frequency;
        try {
            frequency = Double.parseDouble(freqStr);
        } catch (NumberFormatException e) {
            String message = "频率格式不正确";
            return new SpiCommandBuildResult(false, message, message);
        }
        if (frequency < 0) {
            String message = "频率必须大于等于 0";
            return new SpiCommandBuildResult(false, message, message);
        }
        String delayStr = binding.spiSequenceItem.spiFrequencyDelayEdit.getText().toString().trim();
        long delayMs = 0;
        boolean hasDelay = !delayStr.isEmpty();
        if (hasDelay) {
            try {
                delayMs = Long.parseLong(delayStr);
            } catch (NumberFormatException e) {
                String message = "延迟格式不正确";
                return new SpiCommandBuildResult(false, message, message);
            }
            if (delayMs < 0) {
                String message = "延迟不能为负值";
                return new SpiCommandBuildResult(false, message, message);
            }
        }
        int frequencyRegister = Ad9833Controller.computeFrequencyRegister(frequency, AD9833_MCLK_HZ);
        int freqLsbWord = Ad9833Controller.buildFrequencyWord(Ad9833Controller.CHANNEL_0, false, frequencyRegister);
        int freqMsbWord = Ad9833Controller.buildFrequencyWord(Ad9833Controller.CHANNEL_0, true, frequencyRegister);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "AD9833:%04X %04X", freqLsbWord, freqMsbWord));
        if (hasDelay) {
            builder.append(" | ").append(delayMs);
        }
        return new SpiCommandBuildResult(true, builder.toString(), null);
    }

    private SpiCommandBuildResult buildAmplitudeCommand() {
        if (binding == null) {
            return new SpiCommandBuildResult(false, "", "绑定未初始化");
        }
        String ampStr = binding.spiSequenceItem.spiAmplitudeValueEdit.getText().toString().trim();
        if (ampStr.isEmpty()) {
            String message = "请输入幅度";
            return new SpiCommandBuildResult(false, message, message);
        }
        int amplitude;
        try {
            amplitude = Integer.parseInt(ampStr);
        } catch (NumberFormatException e) {
            String message = "幅度格式不正确";
            return new SpiCommandBuildResult(false, message, message);
        }
        if (amplitude < 0 || amplitude > 255) {
            String message = "幅度必须在 0~255";
            return new SpiCommandBuildResult(false, message, message);
        }
        String delayStr = binding.spiSequenceItem.spiAmplitudeDelayEdit.getText().toString().trim();
        long delayMs = 0;
        boolean hasDelay = !delayStr.isEmpty();
        if (hasDelay) {
            try {
                delayMs = Long.parseLong(delayStr);
            } catch (NumberFormatException e) {
                String message = "延迟格式不正确";
                return new SpiCommandBuildResult(false, message, message);
            }
            if (delayMs < 0) {
                String message = "延迟不能为负值";
                return new SpiCommandBuildResult(false, message, message);
            }
        }
        int commandWord = Mcp41010Controller.buildCommandWord(amplitude);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "MCP41010:%04X", commandWord));
        if (hasDelay) {
            builder.append(" | ").append(delayMs);
        }
        return new SpiCommandBuildResult(true, builder.toString(), null);
    }

    private int resolveSelectedWaveformModeBits() {
        if (binding == null) {
            return -1;
        }
        int checkedId = binding.spiSequenceItem.spiWaveformRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.spi_waveform_sine_option) {
            return Ad9833Controller.MODE_BITS_SINE;
        } else if (checkedId == R.id.spi_waveform_triangle_option) {
            return Ad9833Controller.MODE_BITS_TRIANGLE;
        } else if (checkedId == R.id.spi_waveform_square_option) {
            return Ad9833Controller.MODE_BITS_SQUARE2;
        } else if (checkedId == R.id.spi_waveform_square_half_option) {
            return Ad9833Controller.MODE_BITS_SQUARE1;
        }
        return -1;
    }

    private void appendSequenceLine(String line) {
        if (TextUtils.isEmpty(line)) {
            return;
        }
        Editable editable = binding.spiSequenceItem.spiSequenceEdit.getText();
        if (editable.length() > 0 && editable.charAt(editable.length() - 1) != '\n') {
            editable.append('\n');
        }
        editable.append(line);
    }

    private long parseDelayMilliseconds(String delayStr) {
        if (delayStr == null || delayStr.isEmpty()) {
            return 0L;
        }
        try {
            long value = Long.parseLong(delayStr);
            if (value < 0) {
                throw new IllegalArgumentException("默认延迟不能为负值");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("默认延迟格式错误");
        }
    }

    private List<SpiSequenceEntry> parseSpiSequence(String raw, long defaultDelayMs) {
        List<SpiSequenceEntry> entries = new ArrayList<>();
        if (raw == null) {
            return entries;
        }
        String[] lines = raw.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] delaySplit = line.split("\\|", 2);
            String commandPart = delaySplit[0].trim();
            long delayMs = defaultDelayMs;
            if (delaySplit.length > 1) {
                String delayPart = delaySplit[1].trim();
                if (!delayPart.isEmpty()) {
                    try {
                        delayMs = Long.parseLong(delayPart);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("第 " + (i + 1) + " 行延迟格式错误");
                    }
                    if (delayMs < 0) {
                        throw new IllegalArgumentException("第 " + (i + 1) + " 行延迟不能为负值");
                    }
                }
            }
            String[] deviceSplit = commandPart.split(":", 2);
            if (deviceSplit.length != 2) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行格式错误，应为 设备:指令");
            }
            SequenceTarget target = parseSequenceTarget(deviceSplit[0].trim(), i + 1);
            String payload = deviceSplit[1].trim();
            if (payload.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行缺少指令内容");
            }
            String[] wordTokens = payload.split("[ ,]+");
            ArrayList<Integer> words = new ArrayList<>();
            for (String token : wordTokens) {
                if (token == null || token.trim().isEmpty()) {
                    continue;
                }
                words.add(parseHexWord(token.trim(), i + 1));
            }
            if (words.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行无有效指令");
            }
            int[] wordArray = new int[words.size()];
            for (int j = 0; j < words.size(); j++) {
                wordArray[j] = words.get(j);
            }
            entries.add(new SpiSequenceEntry(target, wordArray, delayMs, i + 1));
        }
        return entries;
    }

    private SequenceTarget parseSequenceTarget(String token, int lineNumber) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("第 " + lineNumber + " 行未指定设备");
        }
        String normalized = token.trim().toUpperCase(Locale.US);
        switch (normalized) {
            case "AD9833":
                return SequenceTarget.AD9833;
            case "MCP41010":
            case "MCP":
            case "MCP41":
                return SequenceTarget.MCP41010;
            default:
                throw new IllegalArgumentException("第 " + lineNumber + " 行设备未知：" + token);
        }
    }

    private int parseHexWord(String token, int lineNumber) {
        String normalized = token.toUpperCase(Locale.US);
        if (normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.endsWith("H")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        normalized = normalized.replace("_", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("第 " + lineNumber + " 行指令格式错误");
        }
        int value;
        try {
            value = Integer.parseInt(normalized, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + lineNumber + " 行指令格式错误：" + token);
        }
        return value & 0xFFFF;
    }

    private void executeSpiSequence(List<SpiSequenceEntry> entries) {
        StringBuilder logBuilder = new StringBuilder();
        boolean success = true;
        int step = 1;
        for (SpiSequenceEntry entry : entries) {
            try {
                if (entry.target == SequenceTarget.AD9833) {
                    ad9833Controller.writeRawWordSequence(entry.words, 0);
                } else {
                    mcpController.writeRawWordSequence(entry.words, 0);
                }
                logBuilder.append(String.format(Locale.US,
                        "%02d. [行%d] %s -> %s",
                        step,
                        entry.lineNumber,
                        entry.target.getLabel(),
                        joinWords(entry.words)));
                if (entry.delayMs > 0) {
                    logBuilder.append(String.format(Locale.US, " | 延迟 %d ms", entry.delayMs));
                }
                logBuilder.append('\n');
            } catch (CH341LibException e) {
                success = false;
                logBuilder.append(String.format(Locale.US,
                        "%02d. [行%d] %s 发送失败: %s\n",
                        step,
                        entry.lineNumber,
                        entry.target.getLabel(),
                        e.getMessage()));
                break;
            }
            step++;
            if (entry.delayMs > 0) {
                try {
                    Thread.sleep(entry.delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    success = false;
                    logBuilder.append("执行被中断\n");
                    break;
                }
            }
        }
        final boolean finalSuccess = success;
        final String logText = logBuilder.length() == 0 ? "--" : logBuilder.toString().trim();
        runOnUiThread(() -> {
            binding.spiSequenceItem.spiSequenceLogText.setText(logText);
            showToast(finalSuccess ? "序列发送完成" : "序列发送中断");
        });
    }

    private String formatWord(int word) {
        return String.format(Locale.US, "0x%04X", word & 0xFFFF);
    }

    private String joinWords(int... words) {
        if (words == null || words.length == 0) {
            return "--";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(formatWord(words[i]));
        }
        return builder.toString();
    }

    private void updateMcpValueLabel(int value) {
        binding.mcp41010Item.mcp41010ValueText.setText(String.valueOf(value));
        updateMcpInstructionDetails(value);
    }

    private void handleMcpSetValue() {
        if (!isDeviceOpen || usbDevice == null) {
            showToast("请先打开设备");
            return;
        }
        final int value = binding.mcp41010Item.mcp41010ValueSeek.getProgress();
        ioExec.execute(() -> {
            try {
                mcpController.writeValue(value);
                runOnUiThread(() -> showToast("电位器值已设置为 " + value));
            } catch (CH341LibException e) {
                runOnUiThread(() -> showToast("设置电位器失败: " + e.getMessage()));
            }
        });
    }

    private void initializeAd9833() {
        ioExec.execute(() -> {
            try {
                ad9833Controller.attachDevice(usbDevice);
                ad9833Controller.setCsChannel(0);
                ad9833Controller.begin();
                ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, 0.0);
                ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0);
                ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF);
                runOnUiThread(() -> {
                    binding.ad9833Item.ad9833FreqEdit.setText("0");
                    showToast("AD9833 初始化完成");
                });
            } catch (CH341LibException e) {
                runOnUiThread(() -> showToast("AD9833 初始化失败: " + e.getMessage()));
            }
        });
    }

    private void initializeMcp41010() {
        ioExec.execute(() -> {
            try {
                mcpController.attachDevice(usbDevice);
                mcpController.setCsChannel(1);
                mcpController.writeValue(mcpCurrentValue);
                runOnUiThread(() -> showToast("MCP41010 初始化完成"));
            } catch (CH341LibException e) {
                runOnUiThread(() -> showToast("MCP41010 初始化失败: " + e.getMessage()));
            }
        });
    }

    private void initSpinner(Spinner spinner, @ArrayRes int arrayId){
        String[] stringArray = getResources().getStringArray(arrayId);
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, R.layout.item_spinner, stringArray);
        arrayAdapter.setDropDownViewResource(R.layout.item_spinner);
        spinner.setAdapter(arrayAdapter);
        spinner.setSelection(0);
    }


    /***************************************call back**********************************************/
    private final IUsbStateChange iUsbStateChange = new IUsbStateChange() {
        @Override
        public void usbDeviceDetach(UsbDevice device) {
            //设备拔出
            if(usbDevice!=null){
                if(device.getVendorId()==usbDevice.getVendorId()
                        && device.getProductId()==usbDevice.getProductId()
                        && device.getDeviceName().equals(usbDevice.getDeviceName())){
                    closeDevice();
                }
            }
        }

        @Override
        public void usbDeviceAttach(UsbDevice device) {

        }

        @Override
        public void usbDevicePermission(UsbDevice usbDevice, boolean b) {
            if(!isDeviceOpen){
                openDevice();
            }
        }
    };
    /*************************************** Handler *********************************************/
    //发送消息处理
    private void sendMessage(int what){
        Message msg = new Message();
        msg.what = what;
        mainHandler.sendMessage(msg);
    }

    Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case OPEN_DEVICE: //USB设备打开
                    setBtnEnable(true);
                    setStreamBtnEnable(true,false);
                    setAd9833ControlsEnabled(true);
                    setMcpControlsEnabled(true);
                    setSpiSequenceControlsEnabled(true);
                    binding.btnOpen.setText(getResources().getString(R.string.close_device));
                    break;
                case CLOSE_DEVICE://USB设备关闭
                    setBtnEnable(false);
                    setStreamBtnEnable(false,false);
                    setAd9833ControlsEnabled(false);
                    setMcpControlsEnabled(false);
                    setSpiSequenceControlsEnabled(false);
                    binding.btnOpen.setText(getResources().getString(R.string.open_device));
                    break;
                case STREAM_CONFIG_SUCCESS: //同步串流模式设置成功
                    setStreamBtnEnable(true,true);
                    break;
            }
            super.handleMessage(msg);
        }
    };



    /***************************************Device API*********************************************/
    //打开设备
    private void openDevice(){
        ArrayList<UsbDevice> usbDevices = new ArrayList<>();
        try {
            usbDevices =  CH341Manager.getInstance().enumDevice();
        } catch (CH341LibException e) {
            LogUtil.d("enum device exception:"+e.getMessage());
            showToast("枚举设备失败");
            return;
        }
        if(usbDevices.size()==0){
            showToast("未找到设备");
            return ;
        }
        if(usbDevices.size()!=1){
            showToast("只支持一个设备");
            return ;
        }
        UsbDevice usbDevice = usbDevices.get(0);
        try {
            if (CH341Manager.getInstance().hasPermission(usbDevice)){
                if(!CH341Manager.getInstance().openDevice(usbDevice)){
                    showToast("打开设备失败");
                    return ;
                }
                showToast("打开设备成功");
                isDeviceOpen = true;
                this.usbDevice=usbDevice;
                sendMessage(OPEN_DEVICE);
                initializeMcp41010();
                initializeAd9833();
            }else {
                CH341Manager.getInstance().requestPermission(context,usbDevice);//申请权限
            }
        } catch (CH341LibException | NoPermissionException | ChipException e) {
            LogUtil.d("open device exception:"+e.getMessage());
            showToast("打开设备异常");
        }
    }


    //关闭设备
    private void closeDevice() {
        if (!isDeviceOpen ||  this.usbDevice == null){
            return;
        }
        CH341Manager.getInstance().closeDevice(usbDevice);
        isDeviceOpen = false;
        this.usbDevice=null;
        ad9833Controller.detach();
        mcpController.detach();
        sendMessage(Global.CLOSE_DEVICE);
    }

    /***************************************EPP API*********************************************/
    private boolean ch341EPPInit(){
        byte mode = 0x00;
        try {
            if (!CH341Manager.getInstance().CH34xSetParaMode(this.usbDevice,mode)){
                return false;
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xSetParaMode exception:"+e.getMessage());
            return false;
        }
        try {
            if (!CH341Manager.getInstance().CH34xInitParallel(this.usbDevice,mode)){
                return false;
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xSetParaMode exception:"+e.getMessage());
            return false;
        }
        return true;
    }

    //EPP读数据
    private void handleEppReadData() {
        String readDataLenStr = binding.eppItem.eppRwDataLenEdit.getText().toString();
        int readLen = 0;
        if (readDataLenStr.equals("")){
            showToast("读取长度为空");
            return;
        }
        readLen = FormatUtil.hexToInt(readDataLenStr);
        if (readLen <= 0 || readLen >=0x1000){
            showToast("读取长度填写错误");
            return;
        }
        if (!ch341EPPInit()){
            showToast("初始化EPP失败");
            return;
        }
        byte[] buffer = new byte[readLen];
        try {
            if (CH341Manager.getInstance().CH34xEppReadData(this.usbDevice,buffer,readLen)){
                binding.eppItem.eppRwDataEdit.setText(FormatUtil.bytesToHexString(buffer));
                showToast("读取成功");
            }else {
                showToast("读取失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xEppReadData exception:"+e.getMessage());
            showToast("读取出错");
        }
    }

    //EPP写数据
    private void handleEppWriteData() {
        String readDataLenStr = binding.eppItem.eppRwDataLenEdit.getText().toString();
        String readDataStr = binding.eppItem.eppRwDataEdit.getText().toString();
        int readLen = 0;
        if (readDataLenStr.equals("") || readDataStr.equals("")){
            showToast("写入长度或数据为空");
            return;
        }
        readLen = FormatUtil.hexToInt(readDataLenStr);
        if (readLen <= 0 || readLen >=0x1000){
            showToast("写入长度填写错误");
            return;
        }
        if (!readDataStr.matches("([0-9|a-f|A-F]{2})*")){
            showToast("输入内容不符合hex规范");
            return;
        }
        if (!ch341EPPInit()){
            showToast("初始化EPP失败");
            return;
        }
        byte[] data = FormatUtil.hexStringToBytes(readDataStr);
        if (data.length != readLen){
            showToast("输出内容与长度不一致");
            return;
        }

        try {
            if (CH341Manager.getInstance().CH34xEppWriteData(this.usbDevice,data,readLen)){
                showToast("写成功");
            }else {
                showToast("写失败");
            }
        } catch (CH341LibException e) {
            showToast("写异常");
        }
    }


    //EPP地址读
    private void handleEppReadAddr() {
        String readDataLenStr = binding.eppItem.eppRwAddrLenEdit.getText().toString();
        int readLen = 0;
        if (readDataLenStr.equals("")){
            showToast("读取长度为空");
            return;
        }
        readLen = FormatUtil.hexToInt(readDataLenStr);
        if (readLen <= 0 || readLen >=0x1000){
            showToast("读取长度填写错误");
            return;
        }
        if (!ch341EPPInit()){
            showToast("初始化EPP失败");
            return;
        }
        byte[] buffer = new byte[readLen];
        try {
            if (CH341Manager.getInstance().CH34xEppReadAddr(this.usbDevice,buffer,readLen)){
                binding.eppItem.eppRwAddrEdit.setText(FormatUtil.bytesToHexString(buffer));
                showToast("读取成功");
            }else {
                showToast("读取失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xEppReadData exception:"+e.getMessage());
            showToast("读取出错");
        }
    }

    //EPP地址写
    private void handleEppWriteAddr() {
        String writeAddrLenStr = binding.eppItem.eppRwAddrLenEdit.getText().toString();
        String writeAddrStr = binding.eppItem.eppRwAddrEdit.getText().toString();
        int writeLen = 0;
        if (writeAddrLenStr.equals("") || writeAddrStr.equals("")){
            showToast("写入长度或数据为空");
            return;
        }
        writeLen = FormatUtil.hexToInt(writeAddrLenStr);
        if (writeLen <= 0 || writeLen >=0x1000){
            showToast("写入长度填写错误");
            return;
        }
        if (!writeAddrStr.matches("([0-9|a-f|A-F]{2})*")){
            showToast("输入内容不符合hex规范");
            return;
        }
        if (!ch341EPPInit()){
            showToast("初始化EPP失败");
            return;
        }
        byte[] data = FormatUtil.hexStringToBytes(writeAddrStr);
        if (data.length != writeLen){
            showToast("输出内容与长度不一致");
            return;
        }
        try {
            if (CH341Manager.getInstance().CH34xEppWriteAddr(this.usbDevice,data,writeLen)){
                showToast("写成功");
            }else {
                showToast("写失败");
            }
        } catch (CH341LibException e) {
            showToast("写异常");
        }
    }

    /***************************************MEM API*********************************************/
    private boolean ch341MEMInit(){
        try {
            return CH341Manager.getInstance().CH34xInitMEM(usbDevice);
        } catch (CH341LibException e) {
            return false;
        }
    }
    //MEM读数据
    private void handleMemReadData() {
        String readDataLenStr = binding.memItem.memRwDataLenEdit.getText().toString();
        int readLen = 0;
        if (readDataLenStr.equals("")){
            showToast("读取长度为空");
            return;
        }
        readLen = FormatUtil.hexToInt(readDataLenStr);
        if (readLen <= 0 || readLen >=0x1000){
            showToast("读取长度填写错误");
            return;
        }
        if (!ch341MEMInit()){
            showToast("初始化MEM失败");
            return;
        }
        byte[] buffer = new byte[readLen];
        try {
            if (CH341Manager.getInstance().CH34xMEMReadData(this.usbDevice,buffer,readLen, (byte) 0x00)){
                binding.memItem.memRwDataEdit.setText(FormatUtil.bytesToHexString(buffer));
                showToast("读取成功");
            }else {
                showToast("读取失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xMEMReadData exception:"+e.getMessage());
            showToast(e.getMessage());
        }
    }

    //MEM写数据
    private void handleMemWriteData() {
        String writeDataLenStr = binding.memItem.memRwDataLenEdit.getText().toString();
        String writeDataStr = binding.memItem.memRwDataEdit.getText().toString();
        int writeLen = 0;
        if (writeDataLenStr.equals("") || writeDataStr.equals("")){
            showToast("写入长度或数据为空");
            return;
        }
        writeLen = FormatUtil.hexToInt(writeDataLenStr);
        if (writeLen <= 0 || writeLen >=0x1000){
            showToast("写入长度填写错误");
            return;
        }
        if (!writeDataStr.matches("([0-9|a-f|A-F]{2})*")){
            showToast("输入内容不符合hex规范");
            return;
        }
        if (!ch341MEMInit()){
            showToast("初始化MEM失败");
            return;
        }
        byte[] data = FormatUtil.hexStringToBytes(writeDataStr);
        if (data.length != writeLen){
            showToast("输出内容与长度不一致");
            return;
        }
        try {
            if (CH341Manager.getInstance().CH34xMEMWriteData(this.usbDevice,data,writeLen,(byte) 0)){
                showToast("写成功");
            }else {
                showToast("写失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xMEMWriteData exception:"+e.getMessage());
            showToast(e.getMessage());
        }
    }

    /****************************************stream API***********************************************/
    //Stream Config
    private void handleStreamConfig(){
        int workRate = binding.streamItem.streamWorkRateSpinner.getSelectedItemPosition();
        int workMode = binding.streamItem.spiWorkModeSpinner.getSelectedItemPosition();
        int dataMode = binding.streamItem.spiDataModeSpinner.getSelectedItemPosition();
        byte mode  = 0x00;
        mode = FormatUtil.byteBitChange(mode,0,workRate & 0x1);
        mode = FormatUtil.byteBitChange(mode,1,(workRate >> 1) & 0x1);
        mode = FormatUtil.byteBitChange(mode,2,workMode);
        mode = FormatUtil.byteBitChange(mode,7,dataMode);
        try {
            if(CH341Manager.getInstance().CH34xSetStream(usbDevice,mode)){
                showToast("设置成功");
            }else {
                showToast("设置失败");
            }
        } catch (CH341LibException e) {
            showToast(e.getMessage());
            return;
        }
        sendMessage(STREAM_CONFIG_SUCCESS);
    }
    private int getSPIChipSelect(){
        byte iChipSelect = 0x00;
        if (binding.streamItem.spi.spiChipSelectCheck.isChecked()){
            iChipSelect = FormatUtil.byteBitChange(iChipSelect,7,1);
        }
        int chipSelect =  binding.streamItem.spi.spiChipSelectSpinner.getSelectedItemPosition();
        iChipSelect =  FormatUtil.byteBitChange(iChipSelect,0,chipSelect&0x1);
        iChipSelect =  FormatUtil.byteBitChange(iChipSelect,1,(chipSelect>>1)&0x1);
        return iChipSelect & 0x000000FF;
    }

    //SPI 读写
    private void handleSPIRW(){
        int iChipSelect = getSPIChipSelect();
        String dataLenStr = binding.streamItem.spi.spiRwDataLenEdit.getText().toString();
        String writeDataStr = binding.streamItem.spi.spiWriteDataEdit.getText().toString();
        int writeLen = 0;
        if (dataLenStr.equals("") || writeDataStr.equals("") ){
            showToast("读写长度或待写数据为空");
            return;
        }
        writeLen = FormatUtil.hexToInt(dataLenStr);
        if (writeLen <= 0){
            showToast("写入长度填写错误");
            return;
        }
        if (!writeDataStr.matches("([0-9|a-f|A-F]{2})*")){
            showToast("输入内容不符合hex规范");
            return;
        }
        byte[] data = FormatUtil.hexStringToBytes(writeDataStr);
        if (data.length != writeLen){
            showToast("输出内容与长度不一致");
            return;
        }
        try {
            if (CH341Manager.getInstance().CH34xStreamSPI4(this.usbDevice,iChipSelect,writeLen,data)){
                binding.streamItem.spi.spiReadDataEdit.setText(FormatUtil.bytesToHexString(data));
                showToast("读写成功");
            }else {
                showToast("读写失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xStreamSPI4 exception:"+e.getMessage());
            showToast(e.getMessage());
        }

    }

    //I2C 读写
    private void handleI2CRW(){
        String writeLenStr = binding.streamItem.i2c.i2cWriteDataLenEdit.getText().toString();
        String writeDataStr = binding.streamItem.i2c.i2cWriteDataEdit.getText().toString();
        String readLenStr = binding.streamItem.i2c.i2cReadDataLenEdit.getText().toString();
        int writeLen = 0;
        int readLen = 0;
        if (!writeLenStr.equals("")){
            writeLen = FormatUtil.hexToInt(writeLenStr);
        }
        if (!readLenStr.equals("")){
            readLen = FormatUtil.hexToInt(readLenStr);
        }
        if (writeLen < 0 || readLen <0){
            showToast("读写长度填写错误");
            return;
        }
        if(writeLen == 0 &&  readLen == 0 ){
            showToast("读写长度全为空");
            return;
        }
        byte[] writeData = new byte[writeLen];
        byte[] readData = new byte[readLen];
        if (writeLen > 0 ) {
            if (!writeDataStr.matches("([0-9|a-f|A-F]{2})*")){
                showToast("输入内容不符合hex规范");
                return;
            }
            byte[] data = FormatUtil.hexStringToBytes(writeDataStr);
            if (data.length != writeLen){
                showToast("输出内容与长度不一致");
                return;
            }
            System.arraycopy(data,0,writeData,0,writeLen);
        }

        try {
            if (CH341Manager.getInstance().CH34xStreamI2C(this.usbDevice,writeLen,writeData,readLen,readData)){
                binding.streamItem.i2c.i2cReadDataEdit.setText(FormatUtil.bytesToHexString(readData));
                showToast("I2C读写成功");
            }else {
                showToast("I2C读写失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xStreamI2C exception:"+e.getMessage());
            showToast("读写异常");
        }
    }

    //EEPROM 写
    private void handelEEPROMWrite(){
        String addrStr = binding.streamItem.eeprom.eepromWriteAddrEdit.getText().toString();
        String writeDataLenStr = binding.streamItem.eeprom.eepromWriteDataLenEdit.getText().toString();
        String writeDataStr = binding.streamItem.eeprom.eepromWriteDataEdit.getText().toString();
        int writeLen = 0;
        int addr = 0;
        if (writeDataLenStr.equals("") || writeDataStr.equals("") || addrStr.equals("") ){
            showToast("有写参数为空");
            return;
        }
        addr = FormatUtil.hexToInt(addrStr);
        if (addr < 0 ){
            showToast("地址填写错误");
            return;
        }
        writeLen = FormatUtil.hexToInt(writeDataLenStr);
        if (writeLen <= 0 || writeLen >=0x400){
            showToast("写入长度填写错误");
            return;
        }
        if (!writeDataStr.matches("([0-9|a-f|A-F]{2})*")){
            showToast("输入内容不符合hex规范");
            return;
        }
        byte[] data = FormatUtil.hexStringToBytes(writeDataStr);
        if (data.length != writeLen){
            showToast("输出内容与长度不一致");
            return;
        }
        String typeStr = binding.streamItem.eeprom.eepromTypeSpinner.getSelectedItem().toString();
        EEPROM_TYPE eepromType = EEPROM_TYPE.valueOf(typeStr);
        try {
            if (CH341Manager.getInstance().CH34xWriteEEPROM(this.usbDevice,eepromType,addr,writeLen,data)){
                showToast("写成功");
            }else {
                showToast("写失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xWriteEEPROM exception:"+e.getMessage());
            showToast(e.getMessage());
        }
    }
    //EEPROM 写
    private void handelEEPROMRead(){
        String addrStr = binding.streamItem.eeprom.eepromReadAddrEdit.getText().toString();
        String readLenStr = binding.streamItem.eeprom.eepromReadDataLenEdit.getText().toString();
        int readLen = 0;
        int addr = 0;
        if (readLenStr.equals("") || addrStr.equals("") ){
            showToast("有参数为空");
            return;
        }
        addr = FormatUtil.hexToInt(addrStr);
        if (addr < 0 ){
            showToast("地址填写错误");
            return;
        }
        readLen = FormatUtil.hexToInt(readLenStr);
        if (readLen <= 0 || readLen >=0x400){
            showToast("读取长度填写错误");
            return;
        }
        byte[] data = new byte[readLen];

        String typeStr = binding.streamItem.eeprom.eepromTypeSpinner.getSelectedItem().toString();
        EEPROM_TYPE eepromType = EEPROM_TYPE.valueOf(typeStr);
        try {
            if (CH341Manager.getInstance().CH34xReadEEPROM(this.usbDevice,eepromType,addr,readLen,data)){
                binding.streamItem.eeprom.eepromReadDataEdit.setText(FormatUtil.bytesToHexString(data));
                showToast("读成功");
            }else {
                showToast("读失败");
            }
        } catch (CH341LibException e) {
            LogUtil.d("CH34xReadEEPROM exception:"+e.getMessage());
            showToast(e.getMessage());
        }
    }

    /****************************************GPIO API***********************************************/

    //读取GPIO
    private void handelReadGpio() {
        int gpioValue = 0;
        try {
            gpioValue = CH341Manager.getInstance().CH34xGetInput(usbDevice);
        } catch (CH341LibException e) {
            showToast(e.getMessage());
            return;
        }
        binding.gpioItem.cb6.setChecked((gpioValue & 0x00000001)!=0);
        binding.gpioItem.cb7.setChecked((gpioValue & 0x00000002)!=0);
        binding.gpioItem.cb8.setChecked((gpioValue & 0x00000004)!=0);
        binding.gpioItem.cb9.setChecked((gpioValue & 0x00000008)!=0);
        binding.gpioItem.cb10.setChecked((gpioValue & 0x00000010)!=0);
        binding.gpioItem.cb11.setChecked((gpioValue & 0x00000020)!=0);
        binding.gpioItem.cb12.setChecked((gpioValue & 0x00000040)!=0);
        binding.gpioItem.cb13.setChecked((gpioValue & 0x00000080)!=0);
        binding.gpioItem.cb2.setChecked((gpioValue & 0x00000100)!=0);
        binding.gpioItem.cb3.setChecked((gpioValue & 0x00000200)!=0);
        binding.gpioItem.cb4.setChecked((gpioValue & 0x00000400)!=0);
        binding.gpioItem.cb5.setChecked((gpioValue & 0x00000800)!=0);
        binding.gpioItem.cb14.setChecked((gpioValue & 0x00002000)!=0);
        binding.gpioItem.cb1.setChecked((gpioValue & 0x00004000)!=0);
        binding.gpioItem.cb0.setChecked((gpioValue & 0x00008000)!=0);
        binding.gpioItem.cb15.setChecked((gpioValue & 0x00800000)!=0);
    }

    //设置GPIO
    private void handleSetGpio() {
        int gpioDir=0,gpioVal=0;

        //D0
        gpioDir|=(binding.gpioItem.rbOut6.isChecked()?0x00000001:0x00);
        gpioVal|=(binding.gpioItem.cb6.isChecked()?0x00000001:0x00);
        //D1
        gpioDir|=(binding.gpioItem.rbOut7.isChecked()?0x00000002:0x00);
        gpioVal|=(binding.gpioItem.cb7.isChecked()?0x00000002:0x00);
        //D2
        gpioDir|=(binding.gpioItem.rbOut8.isChecked()?0x00000004:0x00);
        gpioVal|=(binding.gpioItem.cb8.isChecked()?0x00000004:0x00);
        //D3
        gpioDir|=(binding.gpioItem.rbOut9.isChecked()?0x00000008:0x00);
        gpioVal|=(binding.gpioItem.cb9.isChecked()?0x00000008:0x00);

        //D4
        gpioDir|=(binding.gpioItem.rbOut10.isChecked()?0x00000010:0x00);
        gpioVal|=(binding.gpioItem.cb10.isChecked()?0x00000010:0x00);
        //D5
        gpioDir|=(binding.gpioItem.rbOut11.isChecked()?0x00000020:0x00);
        gpioVal|=(binding.gpioItem.cb11.isChecked()?0x00000020:0x00);
        //D6
        gpioDir|=(binding.gpioItem.rbOut12.isChecked()?0x00000040:0x00);
        gpioVal|=(binding.gpioItem.cb12.isChecked()?0x00000040:0x00);
        //D7
        gpioDir|=(binding.gpioItem.rbOut13.isChecked()?0x00000080:0x00);
        gpioVal|=(binding.gpioItem.cb13.isChecked()?0x00000080:0x00);
        //ERR
        gpioDir|=(binding.gpioItem.rbOut2.isChecked()?0x00000100:0x00);
        gpioVal|=(binding.gpioItem.cb2.isChecked()?0x00000100:0x00);
        //PEMP
        gpioDir|=(binding.gpioItem.rbOut3.isChecked()?0x00000200:0x00);
        gpioVal|=(binding.gpioItem.cb3.isChecked()?0x00000200:0x00);
        //INT
        gpioDir|=(binding.gpioItem.rbOut4.isChecked()?0x00000400:0x00);
        gpioVal|=(binding.gpioItem.cb4.isChecked()?0x00000400:0x00);
        //SLCT
        gpioDir|=(binding.gpioItem.rbOut5.isChecked()?0x00000800:0x00);
        gpioVal|=(binding.gpioItem.cb5.isChecked()?0x00000800:0x00);

        //BUSY
        gpioDir|=(binding.gpioItem.rbOut14.isChecked()?0x00002000:0x00);
        gpioVal|=(binding.gpioItem.cb14.isChecked()?0x00002000:0x00);

        //AUTOFD
        gpioDir|=(binding.gpioItem.rbOut1.isChecked()?0x00004000:0x00);
        gpioVal|=(binding.gpioItem.cb1.isChecked()?0x00004000:0x00);

        //SLCTIN
        gpioDir|=(binding.gpioItem.rbOut0.isChecked()?0x00008000:0x00);
        gpioVal|=(binding.gpioItem.cb0.isChecked()?0x00008000:0x00);

        //RESET
        gpioDir|=(0x00010000);
        gpioVal|=(binding.gpioItem.cb18.isChecked()?0x00010000:0x00);

        //WRITE
        gpioDir|=(0x00020000);
        gpioVal|=(binding.gpioItem.cb17.isChecked()?0x00020000:0x00);

        //SCL
        gpioDir|=(0x00040000);
        gpioVal|=(binding.gpioItem.cb16.isChecked()?0x00040000:0x00);
        try {
            boolean b = CH341Manager.getInstance().CH34xSetOutput(usbDevice,0x1F,gpioDir,gpioVal);
            showToast(b?"设置成功":"设置失败");
        } catch (CH341LibException e) {
            showToast(e.getMessage());
        }
    }




    private void showToast(String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,message,Toast.LENGTH_SHORT).show();
            }
        });
    }

    private enum SequenceTarget {
        AD9833("AD9833"),
        MCP41010("MCP41010");

        private final String label;

        SequenceTarget(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final class SpiCommandBuildResult {
        final boolean valid;
        final String commandText;
        final String errorMessage;

        SpiCommandBuildResult(boolean valid, String commandText, String errorMessage) {
            this.valid = valid;
            this.commandText = commandText;
            this.errorMessage = errorMessage;
        }
    }

    private static final class SpiSequenceEntry {
        final SequenceTarget target;
        final int[] words;
        final long delayMs;
        final int lineNumber;

        SpiSequenceEntry(SequenceTarget target, int[] words, long delayMs, int lineNumber) {
            this.target = target;
            this.words = words;
            this.delayMs = delayMs;
            this.lineNumber = lineNumber;
        }
    }

    private static DecimalFormat createDecimalFormat(String pattern) {
        DecimalFormat decimalFormat = new DecimalFormat(pattern);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void afterTextChanged(Editable s) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CH341Manager.getInstance().close(this);
        if (ad9833Controller != null) {
            ad9833Controller.detach();
        }
        if (mcpController != null) {
            mcpController.detach();
        }
        ioExec.shutdownNow();
    }
}
