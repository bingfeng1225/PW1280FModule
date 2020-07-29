package cn.haier.bio.medical.lt1280f;


public interface ILT1280FListener {
    void onLT1280FReady();
    void onLT1280FConnected();
    void onLT1280FSwitchWriteModel();
    void onLT1280FSwitchReadModel();
    void onLT1280FPrint(String message);
    void onLT1280FSystemChanged(int type);
    byte[] packageLT1280FResponse(int type);
    void onLT1280FException(Throwable throwable);
    void onLT1280FChanged(byte[] data);
}
