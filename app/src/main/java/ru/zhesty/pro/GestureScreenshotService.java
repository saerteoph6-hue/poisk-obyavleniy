package ru.zhesty.pro;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GestureScreenshotService extends AccessibilityService {
    public static final String PREFS="gesture_settings";
    public static final String KEY_GLOBAL_MODE="global_two_finger_down";
    private static GestureScreenshotService current;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private long lastCapture=0L;
    public static boolean isConnected(){ return current!=null; }
    public static void refreshIfRunning(){ if(current!=null) current.applyMode(); }
    public static void testScreenshot(){ if(current!=null) current.capture(); }

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        current=this;
        applyMode();
    }
    private void applyMode(){
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null) return;
        int flags=info.flags & ~(AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
           |AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
           |AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH);
        if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(KEY_GLOBAL_MODE,false)){
            flags|=AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            flags|=AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES;
        }
        info.flags=flags;
        setServiceInfo(info);
    }
    @Override public boolean onGesture(AccessibilityGestureEvent e){
        if(e.getGestureId()==GESTURE_2_FINGER_SWIPE_DOWN
                &&getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(KEY_GLOBAL_MODE,false)){
            capture();
            return true;
        }
        return super.onGesture(e);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}

    private void capture(){
        long now=SystemClock.elapsedRealtime();
        if(now-lastCapture<1200L) return;
        lastCapture=now;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult screenshot){
                    HardwareBuffer buffer=screenshot.getHardwareBuffer();
                    if(buffer==null){showMessage("Не удалось получить изображение");return;}
                    Bitmap hardwareBitmap;
                    try {
                        ColorSpace cs=screenshot.getColorSpace();
                        if(cs==null) cs=ColorSpace.get(ColorSpace.Named.SRGB);
                        hardwareBitmap=Bitmap.wrapHardwareBuffer(buffer,cs);
                    }finally{ buffer.close(); }
                    if(hardwareBitmap==null){showMessage("Не удалось прочитать изображение");return;}
                    Bitmap bitmap=hardwareBitmap.copy(Bitmap.Config.ARGB_8888,false);
                    hardwareBitmap.recycle();
                    if(bitmap==null){showMessage("Не удалось обработать изображение");return;}
                    io.execute(()->saveScreenshot(bitmap));
                }
                @Override public void onFailure(int errorCode){
                    showMessage("Скриншот недоступен (код "+errorCode+"). Возможно, экран защищён.");
                }
            });
        }catch(SecurityException | IllegalStateException ex){
            showMessage("Android не разрешил снять экран");
        }
    }
    private void saveScreenshot(Bitmap bitmap){
        Uri uri=null;
        try {
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,"Screenshot_"+stamp+".png");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Screenshots");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            uri=getContentResolver().insert(MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
            if(uri==null) throw new IllegalStateException("Could not create image");
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null || !bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
                    throw new IllegalStateException("Could not save image");
            }
            ContentValues complete=new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING,0);
            getContentResolver().update(uri,complete,null,null);
            showMessage("Скриншот сохранён в Галерею");
        }catch(Exception ex){
            if(uri!=null) try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
            showMessage("Не удалось сохранить скриншот");
        }finally{bitmap.recycle();}
    }
    private void showMessage(String msg){
        getMainExecutor().execute(()->Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT).show());
    }
    @Override public void onDestroy(){
        current=null; io.shutdown(); super.onDestroy();
    }
}
