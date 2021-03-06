package com.bibizhaoji.crash;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import com.bibizhaoji.bibiji.utils.Log;
import com.bibizhaoji.bibiji.utils.ToastUtils;

public class CrashHandler implements UncaughtExceptionHandler
{
    public static final String TAG = CrashHandler.class.getSimpleName();
    
    // 系统默认的UncaughtException处理类
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    // CrashHandler实例
    private static CrashHandler INSTANCE = new CrashHandler();
    // 程序的Context对象
    private Context mContext;
    // 格式化日期，作为日志文件名的一部分
    
    @SuppressLint("SimpleDateFormat")
    private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    
    /** 保证只有一个CrashHandler实例 */
    private CrashHandler()
    {
    }
    
    /** 获取CrashHandler实例，单例模式 */
    public static CrashHandler getInstance()
    {
        return INSTANCE;
    }
    
    /**
     * 初始化
     */
    public void init(Context context)
    {
        mContext = context;
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        // 设置该CrashHandler为程序的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
    }
    
    /** 当UncaughtException发生时会转入该函数处理 */
    @Override
    public void uncaughtException(Thread thread, Throwable cause)
    {
        if (!handleException(cause) && mDefaultHandler != null)
        {
            // 如果用户没有处理则让系统默认的异常处理器处理
            mDefaultHandler.uncaughtException(thread, cause);
        }
        else
        {
            try
            {
                Thread.sleep(3000);
            }
            catch (Exception e)
            {
                Log.e(TAG, "error : ", e);
            }
            
            // 退出程序
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }
    
    /**
     * 自定义错误处理，收集错误信息，发送错误报告等操作 return true:如果处理了该异常信息;否则返回false.
     */
    private boolean handleException(final Throwable cause)
    {
        if (cause == null)
        {
            return false;
        }
        
        // 使用Toast来显示异常信息
        ToastUtils.show(mContext, "当您看到这个提示时，说明我们的APP遇到了未知问题，对此我们表示深深的歉意，并努力为您解决这个问题。现在，APP即将退出！", Toast.LENGTH_LONG);
        
        new Thread()
        {
            public void run()
            {
                // 获取设备参数
                String dp = getDeviceParameters(mContext);
                
                // 获取崩溃信息
                String cr = getCrashReasons(cause);
                
                // 生成日志
                String log = dp + cr;
                
                // 保存日志文件
                saveLog(log);
                
                try
                {
                    // 发送带附件的邮件
//                    sendEmail(log);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            };
        }.start();
        
        return true;
    }
    
    /**
     * 获取设备参数
     * 
     * @param context
     * @return
     */
    private String getDeviceParameters(Context context)
    {
        StringBuffer sb = new StringBuffer();
        try
        {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null)
            {
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                format(sb, "VersionName", versionName);
                String versionCode = pi.versionCode + "";
                format(sb, "VersionCode", versionCode);
            }
        }
        catch (NameNotFoundException e)
        {
            Log.e(TAG, "an error occured when collect package info", e);
        }
        
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields)
        {
            try
            {
                // AccessibleTest类中的成员变量为private,故必须进行此操作
                field.setAccessible(true);
                format(sb, field.getName(), field.get(null).toString());
            }
            catch (Exception e)
            {
                Log.e(TAG, "an error occured when collect crash info", e);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化设备参数
     * 
     * @param sb
     * @param key
     * @param value
     */
    private void format(StringBuffer sb, String key, String value)
    {
        sb.append(key);
        sb.append("=");
        sb.append(value);
        sb.append("\n");
    }
    
    /**
     * 获取崩溃原因
     * 
     * @param throwable
     * @return
     */
    private String getCrashReasons(Throwable throwable)
    {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        
        Throwable cause = throwable.getCause();
        while (cause != null)
        {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        
        try
        {
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        
        return writer.toString();
    }
    
    /**
     * 记录日志
     * 
     * @param log
     * @return
     */
    private File saveLog(String log)
    {
        try
        {
            long timestamp = System.currentTimeMillis();
            String time = formatter.format(new Date());
            String fileName = "crash-" + time + "-" + timestamp + ".log";
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            {
                File root = null;
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
                {
                    root = Environment.getExternalStorageDirectory();
                }
                else
                {
                    root = mContext.getCacheDir();
                }
                
                File dir = new File(new File(root, "BiBiJi"), "crash");
                dir.mkdirs();
                
                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(log.getBytes());
                fos.close();
                
                return file;
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "an error occured while writing file...", e);
        }
        
        return null;
    }
    
   
}
