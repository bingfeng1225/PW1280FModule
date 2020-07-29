package cn.haier.bio.medical.lt1280f;

/***
 * 超低温变频、T系列、双系统主控板通讯
 *
 */
public class LT1280FManager {
    private LT1280FSerialPort serialPort;
    private static LT1280FManager manager;

    public static LT1280FManager getInstance() {
        if (manager == null) {
            synchronized (LT1280FManager.class) {
                if (manager == null)
                    manager = new LT1280FManager();
            }
        }
        return manager;
    }

    private LT1280FManager() {

    }

    public void init(String path) {
        if(this.serialPort == null){
            this.serialPort = new LT1280FSerialPort();
            this.serialPort.init(path);
        }
    }

    public void enable() {
        if(null != this.serialPort){
            this.serialPort.enable();
        }
    }

    public void disable() {
        if(null != this.serialPort){
            this.serialPort.disable();
        }
    }

    public void release() {
        if(null != this.serialPort){
            this.serialPort.release();
            this.serialPort = null;
        }
    }

    public void changeListener(ILT1280FListener listener) {
        if(null != this.serialPort){
            this.serialPort.changeListener(listener);
        }
    }
}

