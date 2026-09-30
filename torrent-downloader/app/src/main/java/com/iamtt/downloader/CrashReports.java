package com.iamtt.downloader;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.io.*;
import java.util.*;

/** Device-local diagnostics. No automatic uploads or addon links. */
public final class CrashReports extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                StringWriter out=new StringWriter();error.printStackTrace(new PrintWriter(out));
                getSharedPreferences("crash_report",MODE_PRIVATE).edit()
                    .putString("java","Version "+BuildConfig.VERSION_NAME+" · "+new Date()+"\nThread: "+thread.getName()+"\n"+redact(out.toString())).commit();
            }catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
            else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
    }
    static String redact(String text){
        return text.replaceAll("(?i)(https?|stremio|magnet)://[^\\s]+","[link removed]")
            .replaceAll("(?i)magnet:[^\\s]+","[magnet removed]")
            .replaceAll("(?i)\\b[0-9a-f]{40}\\b","[hash removed]");
    }
    public static void checkpoint(Context context,String stage){
        if(Build.VERSION.SDK_INT>=30)try{
            byte[] bytes=("v"+BuildConfig.VERSION_NAME+" · "+stage).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            context.getSystemService(ActivityManager.class).setProcessStateSummary(Arrays.copyOf(bytes,Math.min(bytes.length,128)));
        }catch(Exception ignored){}
    }
    public static String report(Context context){
        StringBuilder out=new StringBuilder("IAMTT v"+BuildConfig.VERSION_NAME+"\nAndroid "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n");
        String javaReport=context.getSharedPreferences("crash_report",MODE_PRIVATE).getString("java","");
        if(!javaReport.isEmpty())out.append("\nLast uncaught Java error\n").append(javaReport).append("\n");
        if(Build.VERSION.SDK_INT>=30){
            try{
                List<ApplicationExitInfo> exits=context.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(context.getPackageName(),0,5);
                for(ApplicationExitInfo e:exits){
                    out.append("\nProcess exit · ").append(new Date(e.getTimestamp())).append("\n")
                        .append(reason(e.getReason())).append(" · status ").append(e.getStatus()).append("\n")
                        .append(redact(String.valueOf(e.getDescription()))).append("\n")
                        .append("Memory at exit: ").append(e.getPss()).append(" KB PSS\n");
                    byte[] checkpoint=e.getProcessStateSummary();if(checkpoint!=null)out.append("Last transfer step: ").append(new String(checkpoint,java.nio.charset.StandardCharsets.UTF_8)).append("\n");
                    if(e.getReason()==ApplicationExitInfo.REASON_ANR){
                        try(InputStream trace=e.getTraceInputStream()){
                            if(trace!=null){byte[] bytes=new byte[32768];int count=0,n;while(count<bytes.length&&(n=trace.read(bytes,count,bytes.length-count))>0)count+=n;out.append(redact(new String(bytes,0,count,java.nio.charset.StandardCharsets.UTF_8))).append("\n");}
                        }catch(Exception ignored){out.append("Native trace unavailable.\n");}
                    }
                }
                if(exits.isEmpty())out.append("\nAndroid has no recent process exit record.\n");
            }catch(Exception e){out.append("\nAndroid exit records unavailable.\n");}
        }else out.append("\nNative exit records require Android 11 or later.\n");
        if(javaReport.isEmpty())out.append("\nNo saved Java crash. A native crash or system termination may still appear above.\n");
        return out.toString();
    }
    private static String reason(int reason){
        switch(reason){
            case ApplicationExitInfo.REASON_CRASH:return "Java crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return "Native engine/process crash";
            case ApplicationExitInfo.REASON_ANR:return "App not responding";
            case ApplicationExitInfo.REASON_LOW_MEMORY:return "System stopped app for low memory";
            case ApplicationExitInfo.REASON_SIGNALED:return "Process ended by signal";
            case ApplicationExitInfo.REASON_USER_REQUESTED:return "User/system requested stop";
            case ApplicationExitInfo.REASON_EXIT_SELF:return "Process exited itself";
            default:return "Android exit reason "+reason;
        }
    }
}
