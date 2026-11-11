/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import java.util.Locale;
import java.util.regex.*;

/** Package compatibility is separate from the engine's Vulkan feature checks. */
public final class DriverPolicy {
    public static final int UNKNOWN=0,ADRENO=1,MALI=2,XCLIPSE=3;
    private static String lower(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}
    public static int gpuFamily(String report){
        String s=lower(report);
        if(s.contains("adreno"))return ADRENO;
        if(s.contains("xclipse"))return XCLIPSE;
        if(s.contains("mali")||s.contains("immortalis"))return MALI;
        if(s.startsWith("5143|"))return ADRENO;
        if(s.startsWith("13b5|"))return MALI;
        return UNKNOWN;
    }
    public static int packageFamily(String name,String library,String backend){
        String s=lower(name+" "+library+" "+backend);
        if(s.contains("radv")||s.contains("xclipse")||s.contains("radeon"))return XCLIPSE;
        if(s.contains("panvk")||s.contains("panfrost"))return MALI;
        if(s.contains("turnip")||s.contains("adreno")||s.contains("freedreno"))return ADRENO;
        return UNKNOWN;
    }
    public static String name(int family){return family==ADRENO?"Adreno":family==MALI?"Mali":family==XCLIPSE?"Xclipse":"Unknown GPU";}
    public static String incompatibility(int gpu,String report,int driver,String packageName,String version,int sdk,int minimum){
        if(minimum<1||minimum>1000)return "The driver has an invalid minimum Android API.";
        if(sdk<minimum)return "This driver requires Android API "+minimum+" or newer; this device uses API "+sdk+".";
        if(driver==UNKNOWN)return gpu==ADRENO?"":"This package does not identify a supported PanVK or RADV Xclipse driver.";
        if(gpu!=driver)return "This "+name(driver)+" driver cannot be used on "+name(gpu)+" hardware.";
        // This particular upstream release explicitly excludes CSF and Midgard devices.
        if(driver==MALI&&lower(packageName).contains("fristonerr")&&lower(version+" "+packageName).contains("3.7.0")){
            Matcher m=Pattern.compile("mali[- ]*g(\\d+)").matcher(lower(report));
            if(!m.find()||!java.util.Arrays.asList("52","57","68","71","72","76","77","78").contains(m.group(1)))
                return "FristOneRR PanVK 3.7.0 does not list this Mali GPU as supported. Use the system driver or a package supporting this GPU and kernel.";
        }
        return "";
    }
}
