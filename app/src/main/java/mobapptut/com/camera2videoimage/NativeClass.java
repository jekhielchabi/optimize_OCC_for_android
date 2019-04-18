package mobapptut.com.camera2videoimage;

/**
 * Created by phuoc on 12/6/2016.
 */

public class NativeClass {
    public native static int convertGray(long matAddrRgba, long matAddrGray);
    public native static void lightDetection(long addrRgba);
    public native static String getJniStringBytes();
}
