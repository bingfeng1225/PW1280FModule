package cn.haier.bio.medical.lt1280f;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.lang.ref.WeakReference;

import cn.qd.peiwen.serialport.PWSerialPortHelper;
import cn.qd.peiwen.serialport.PWSerialPortListener;
import cn.qd.peiwen.serialport.PWSerialPortState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class LT1280FSerialPort implements PWSerialPortListener {
    private ByteBuf buffer;
    private HandlerThread thread;
    private LT1280FHandler handler;
    private PWSerialPortHelper helper;

    private byte system = 0x00;
    private boolean ready = false;
    private boolean enabled = false;
    private WeakReference<ILT1280FListener> listener;

    public LT1280FSerialPort() {
    }

    public void init(String path) {
        this.createHandler();
        this.createHelper(path);
        this.createBuffer();
    }

    public void enable() {
        if (this.isInitialized() && !this.enabled) {
            this.enabled = true;
            this.helper.open();
        }
    }

    public void disable() {
        if (this.isInitialized() && this.enabled) {
            this.enabled = false;
            this.helper.close();
        }
    }

    public void release() {
        this.listener = null;
        this.destoryHandler();
        this.destoryHelper();
        this.destoryBuffer();
    }

    public void changeListener(ILT1280FListener listener) {
        this.listener = new WeakReference<>(listener);
    }

    private boolean isInitialized() {
        if (this.handler == null) {
            return false;
        }
        if (this.helper == null) {
            return false;
        }
        return this.buffer != null;
    }

    private void createHelper(String path) {
        if (this.helper == null) {
            this.helper = new PWSerialPortHelper("LTBSerialPort");
            this.helper.setTimeout(2);
            this.helper.setPath(path);
            this.helper.setBaudrate(9600);
            this.helper.init(this);
        }
    }

    private void destoryHelper() {
        if (null != this.helper) {
            this.helper.release();
            this.helper = null;
        }
    }

    private void createHandler() {
        if (this.thread == null && this.handler == null) {
            this.thread = new HandlerThread("LT1280FSerialPort");
            this.thread.start();
            this.handler = new LT1280FHandler(this.thread.getLooper());
        }
    }

    private void destoryHandler() {
        if (null != this.thread) {
            this.thread.quitSafely();
            this.thread = null;
            this.handler = null;
        }
    }

    private void createBuffer() {
        if (this.buffer == null) {
            this.buffer = Unpooled.buffer(4);
        }
    }

    private void destoryBuffer() {
        if (null != this.buffer) {
            this.buffer.release();
            this.buffer = null;
        }
    }

    private void write(byte[] data) {
        if (!this.isInitialized() || !this.enabled) {
            return;
        }
        this.helper.writeAndFlush(data);
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FPrint("LT1280FSerialPort Send:" + LT11280FTools.bytes2HexString(data, true, ", "));
        }
    }

    private void switchReadModel() {
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FSwitchReadModel();
        }
    }

    private void switchWriteModel() {
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FSwitchWriteModel();
        }
    }

    private boolean ignorePackage() {
        if (this.system == 0x00) {
            for (byte item : LT11280FTools.SYSTEM_TYPES) {
                byte[] bytes = new byte[]{item, 0x10, 0x40, 0x1F};
                int index = LT11280FTools.indexOf(this.buffer, bytes);
                if (index != -1) {
                    byte[] data = new byte[index];
                    this.buffer.readBytes(data, 0, data.length);
                    this.buffer.discardReadBytes();
                    if (null != this.listener && null != this.listener.get()) {
                        this.listener.get().onLT1280FPrint("LT1280FSerialPort 指令丢弃:" + LT11280FTools.bytes2HexString(data, true, ", "));
                    }
                    return this.processBytesBuffer();
                }
            }
        } else {
            byte[] bytes = new byte[]{this.system, 0x10, 0x40, 0x1F};
            int index = LT11280FTools.indexOf(this.buffer, bytes);
            if (index != -1) {
                byte[] data = new byte[index];
                this.buffer.readBytes(data, 0, data.length);
                this.buffer.discardReadBytes();
                if (null != this.listener && null != this.listener.get()) {
                    this.listener.get().onLT1280FPrint("LT1280FSerialPort 指令丢弃:" + LT11280FTools.bytes2HexString(data, true, ", "));
                }
                return this.processBytesBuffer();
            }
        }
        return false;
    }


    private boolean processBytesBuffer() {
        if (this.buffer.readableBytes() < 4) {
            return true;
        }

        byte system = this.buffer.getByte(0);
        byte command = this.buffer.getByte(1);
        if (!LT11280FTools.checkSystemType(system) || !LT11280FTools.checkCommandType(command)) {
            return this.ignorePackage();
        }
        int lenth = (command == 0x10) ? 109 : 8;
        if (this.buffer.readableBytes() < lenth) {
            return true;
        }
        this.buffer.markReaderIndex();
        byte[] data = new byte[lenth];
        byte model = this.buffer.getByte(2);
        this.buffer.readBytes(data, 0, lenth);
        if (!LT11280FTools.checkFrame(data)) {
            this.buffer.resetReaderIndex();
            //当前包不合法 丢掉正常的包头以免重复判断
            this.buffer.skipBytes(4);
            this.buffer.discardReadBytes();
            return this.ignorePackage();
        }
        this.buffer.discardReadBytes();
        if (!this.ready) {
            this.ready = true;
            if (null != this.listener && null != this.listener.get()) {
                this.listener.get().onLT1280FReady();
            }
        }
        if (this.system != system) {
            this.system = system;
            if (null != this.listener && null != this.listener.get()) {
                this.listener.get().onLT1280FSystemChanged(this.system);
            }
        }
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FPrint("LT1280FSerialPort Recv:" + LT11280FTools.bytes2HexString(data, true, ", "));
        }
        this.switchWriteModel();
        Message msg = Message.obtain();
        msg.what = command;
        if (command == 0x10) {
            msg.obj = data;
        } else {
            msg.arg1 = model & 0xFF;
        }
        this.handler.sendMessage(msg);
        return true;
    }

    @Override
    public void onConnected(PWSerialPortHelper helper) {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        this.ready = false;
        this.buffer.clear();
        this.system = 0x00;
        this.switchReadModel();
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FConnected();
        }
    }

    @Override
    public void onReadThreadReleased(PWSerialPortHelper helper) {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FPrint("LTBSerialPort read thread released");
        }
    }

    @Override
    public void onException(PWSerialPortHelper helper, Throwable throwable) {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        this.ready = false;
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FException(throwable);
        }
    }

    @Override
    public void onStateChanged(PWSerialPortHelper helper, PWSerialPortState state) {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        if (null != this.listener && null != this.listener.get()) {
            this.listener.get().onLT1280FPrint("LT1280FSerialPort state changed: " + state.name());
        }
    }

    @Override
    public boolean onByteReceived(PWSerialPortHelper helper, byte[] buffer, int length) throws IOException {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return false;
        }
        this.buffer.writeBytes(buffer, 0, length);
        return this.processBytesBuffer();
    }


    private class LT1280FHandler extends Handler {
        public LT1280FHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0x10: {
                    byte[] data = (byte[]) msg.obj;
                    byte[] response = LT11280FTools.packageStateResponse(data);
                    LT1280FSerialPort.this.write(response);
                    LT1280FSerialPort.this.switchReadModel();
                    if (null != LT1280FSerialPort.this.listener && null != LT1280FSerialPort.this.listener.get()) {
                        LT1280FSerialPort.this.listener.get().onLT1280FChanged(data);
                    }
                    break;
                }
                case 0x03: {
                    byte[] response = null;
                    if (null != LT1280FSerialPort.this.listener && null != LT1280FSerialPort.this.listener.get()) {
                        response = LT1280FSerialPort.this.listener.get().packageLT1280FResponse(msg.arg1);
                    }
                    if (null != response && response.length > 0) {
                        LT1280FSerialPort.this.write(response);
                    }
                    LT1280FSerialPort.this.switchReadModel();
                    break;
                }
                default:
                    break;
            }
        }
    }
}
