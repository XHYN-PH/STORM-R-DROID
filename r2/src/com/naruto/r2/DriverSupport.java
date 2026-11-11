/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import android.content.Context;
import android.os.Build;
import com.naruto.port.LauncherActivity;
import org.json.JSONObject;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Keep the stock driver picker and native AdrenoTools loader; validate their inputs. */
public final class DriverSupport {
    private static String gpu;
    private static native String systemGpu();
    public static synchronized String gpu(){
        if(gpu==null){
            try{System.loadLibrary("xhyngpu");gpu=systemGpu();}catch(LinkageError|RuntimeException unavailable){gpu="";}
            if(gpu==null)gpu="";
            if(gpu.isEmpty())try{
                String soc=Build.VERSION.SDK_INT>=31?String.valueOf(Build.class.getField("SOC_MANUFACTURER").get(null)):"";
                if(Build.HARDWARE.toLowerCase(Locale.ROOT).contains("qcom")||soc.equalsIgnoreCase("QTI")||soc.equalsIgnoreCase("Qualcomm"))gpu="5143|Adreno (system probe unavailable)";
            }catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        return gpu;
    }
    public static boolean available(){return DriverPolicy.gpuFamily(gpu())!=DriverPolicy.UNKNOWN;}
    public static File root(Context c){return new File(c.getFilesDir(),"gpu_drivers");}
    private static File folder(Context c,String id)throws IOException{
        if(id==null||id.isEmpty()||id.startsWith(".")||!id.matches("[A-Za-z0-9_.-]+"))throw new IOException("Invalid selected driver.");
        File base=root(c).getCanonicalFile(),folder=new File(base,id);
        if(Files.isSymbolicLink(folder.toPath())||!folder.getCanonicalFile().getParentFile().equals(base))throw new IOException("Invalid driver folder.");
        return folder;
    }
    public static JSONObject metadata(File folder)throws IOException{
        File meta=new File(folder,"meta.json");
        if(Files.isSymbolicLink(meta.toPath())||!meta.isFile()||meta.length()>65536)throw new IOException("The driver needs a valid meta.json file.");
        try{return new JSONObject(new String(Files.readAllBytes(meta.toPath()),StandardCharsets.UTF_8));}
        catch(org.json.JSONException e){throw new IOException("Driver metadata is not valid JSON.",e);}
    }
    public static a.h validate(File folder,int sdk,String report)throws IOException{
        JSONObject meta=metadata(folder);
        if(meta.has("schemaVersion")&&meta.optInt("schemaVersion",-1)!=1)throw new IOException("Unsupported driver metadata format.");
        String library=meta.optString("libraryName","");DriverArchive.library(folder,library);
        String label=meta.optString("name",folder.getName()),version=meta.optString("driverVersion","");
        String abi=meta.optString("abi","").toLowerCase(Locale.ROOT),api=meta.optString("api","").toLowerCase(Locale.ROOT);
        if(abi.contains("glibc")||(api.contains("opengl")&&!api.contains("vulkan")))throw new IOException("Choose an Android/Bionic Vulkan driver package.");
        if((label+" "+library).toLowerCase(Locale.ROOT).contains("wayland"))throw new IOException("Choose the standard Android driver ZIP, rather than its Wayland build.");
        int family=DriverPolicy.packageFamily(label,library,meta.optString("backend",""));
        String issue=DriverPolicy.incompatibility(DriverPolicy.gpuFamily(report),report,family,label,version,sdk,meta.has("minApi")?meta.optInt("minApi",-1):28);
        if(!issue.isEmpty())throw new IOException(issue);
        String description=meta.optString("description","");
        if(family==DriverPolicy.MALI||family==DriverPolicy.XCLIPSE)description+="\n\nExperimental "+DriverPolicy.name(family)+" driver import. STORM R gameplay compatibility is unverified; GPU, kernel and Vulkan features still matter. Applies at the next game launch.";
        return new a.h(folder.getName(),label,description,version,library);
    }
    public static a.h importDriver(Context c,InputStream source)throws IOException{
        File base=root(c);if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Cannot create the private driver folder.");
        File stage=null;
        try(RandomAccessFile lease=new RandomAccessFile(new File(c.getFilesDir(),"r2-game.lock"),"rw")){
            FileLock lock;try{lock=lease.getChannel().tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Close the game before importing a driver.");}
            if(lock==null)throw new IOException("Close the game before importing a driver.");
            try(FileLock owned=lock){
                stage=Files.createTempDirectory(base.toPath(),".import-").toFile();DriverArchive.extract(source,stage);
                a.h driver=validate(stage,Build.VERSION.SDK_INT,gpu());
                String hash=DriverArchive.packageHash(stage);
                File installed=new File(base,"driver_"+hash.substring(0,32));
                if(installed.exists()){
                    if(!hash.equals(DriverArchive.packageHash(installed)))throw new IOException("The existing copy is damaged. Remove it and import the ZIP again.");
                    validate(installed,Build.VERSION.SDK_INT,gpu());
                }else if(!stage.renameTo(installed))throw new IOException("Could not install the driver.");
                return validate(installed,Build.VERSION.SDK_INT,gpu());
            }
        }finally{if(stage!=null&&stage.exists())DriverArchive.discard(stage);}
    }
    public static a.h selected(LauncherActivity a){
        String id=GameHooks.prefs(a).getString("gpu_driver","");
        if(id==null||id.isEmpty()){GameHooks.prefs(a).edit().remove("r24_driver_issue").apply();return null;}
        try{a.h driver=validate(folder(a,id),Build.VERSION.SDK_INT,gpu());GameHooks.prefs(a).edit().remove("r24_driver_issue").apply();return driver;}
        catch(IOException failure){GameHooks.prefs(a).edit().putString("r24_driver_issue",failure.getMessage()).apply();return null;}
    }
    public static void select(LauncherActivity a,String id){
        String next=id==null?"":id;
        try(RandomAccessFile lease=new RandomAccessFile(new File(a.getFilesDir(),"r2-game.lock"),"rw")){
            FileLock lock;try{lock=lease.getChannel().tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Return to the launcher and wait for the game to close before changing drivers.");}
            if(lock==null)throw new IOException("Return to the launcher and wait for the game to close before changing drivers.");
            try(FileLock owned=lock){
                if(!next.isEmpty())validate(folder(a,next),Build.VERSION.SDK_INT,gpu());
                // A failure from a different ICD must not block trying the newly selected one.
                if(!next.equals(GameHooks.prefs(a).getString("gpu_driver",""))&&a.getExternalFilesDir(null)!=null)Files.deleteIfExists(new File(a.getExternalFilesDir(null),"shadowzero.txt").toPath());
                a.r2OriginalSelectDriver(next);
            }
        }catch(IOException failure){new ModernUi.Builder(a).setTitle("Driver unavailable").setMessage(failure.getMessage()+"\n\nThe previous selection is kept.").setPositiveButton("OK",null).show();}
    }
    public static String selectedLabel(Context c){
        String id=GameHooks.prefs(c).getString("gpu_driver","");if(id==null||id.isEmpty())return "System Vulkan";
        try{JSONObject meta=metadata(folder(c,id));return meta.optString("name","Custom")+" / "+meta.optString("driverVersion","")+" / "+meta.optString("libraryName","");}
        catch(IOException unavailable){return "Custom (metadata unavailable)";}
    }
    public static String information(Context c){
        String issue=GameHooks.prefs(c).getString("r24_driver_issue","");
        return "System GPU: "+(gpu().isEmpty()?"Unavailable":gpu().replace('|',' '))+"\nSelected: "+selectedLabel(c)
            +(issue.isEmpty()?"":"\nSelection blocked: "+issue)
            +"\n\nTurnip: compatible Adreno devices.\nPanVK: compatible Mali devices (experimental).\nRADV Xclipse: supported Xclipse devices (experimental).\n\nImport the Android Vulkan ZIP matching the GPU and Android version. Changes apply on the next game launch. Use the system driver if gameplay fails.";
    }
}
