/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Bounded private staging for the app's existing flat AdrenoTools ZIP format. */
public final class DriverArchive {
    public static final long MAX_ZIP=64L*1024*1024,MAX_FILES=256L*1024*1024;
    public static void extract(InputStream source,File directory)throws IOException{
        Set<String> names=new HashSet<>();long total=0;int entries=0;
        FilterInputStream bounded=new FilterInputStream(source){long bytes;
            private void count(int n)throws IOException{if(n>0&&((bytes+=n)>MAX_ZIP))throw new IOException("Driver ZIP exceeds 64 MB.");}
            @Override public int read()throws IOException{int value=in.read();count(value<0?0:1);return value;}
            @Override public int read(byte[] b,int o,int n)throws IOException{int value=in.read(b,o,n);count(value);return value;}
        };
        try(ZipInputStream zip=new ZipInputStream(bounded)){
            ZipEntry entry;byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null){
                if(++entries>128)throw new IOException("Driver ZIP contains too many entries.");
                String path=entry.getName();if(path.startsWith("/")||path.indexOf('\\')>=0||path.indexOf(':')>=0||path.indexOf('\0')>=0)throw new IOException("Invalid driver ZIP path.");
                for(String part:path.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Invalid driver ZIP path.");
                if(entry.isDirectory()){
                    // getNextEntry() would otherwise inflate a directory's hidden payload outside our size counter.
                    if(zip.read(buffer)!=-1)throw new IOException("Driver ZIP directory entries must be empty.");
                    continue;
                }
                String name=new File(path).getName();
                if(name.isEmpty()||name.startsWith(".")||!names.add(name))throw new IOException("Duplicate or invalid filename in driver ZIP.");
                try(OutputStream out=new FileOutputStream(new File(directory,name))){int n,empty=0;
                    while((n=zip.read(buffer))!=-1){if(n==0){if(++empty>128)throw new IOException("Driver stream stalled.");continue;}empty=0;
                        total+=n;if(total>MAX_FILES)throw new IOException("Expanded driver exceeds 256 MB.");out.write(buffer,0,n);
                    }
                }
            }
        }
        if(names.isEmpty())throw new IOException("Driver ZIP is empty.");
    }
    public static File library(File directory,String name)throws IOException{
        if(name==null||!name.matches("[A-Za-z0-9_+.-]+\\.so")||name.startsWith("."))throw new IOException("Invalid driver libraryName; choose an Android Vulkan driver ZIP.");
        File file=new File(directory,name);
        if(Files.isSymbolicLink(file.toPath())||!file.isFile()||!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))throw new IOException("Driver library is missing.");
        byte[] h=new byte[64];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(h);}
        if(h[0]!=127||h[1]!='E'||h[2]!='L'||h[3]!='F'||h[4]!=2||h[5]!=1||h[6]!=1||h[16]!=3||h[17]!=0||(h[18]&255)!=183||h[19]!=0)throw new IOException("The driver must be an ARM64 Android shared library.");
        return file;
    }
    public static String hash(File file)throws IOException{
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
            StringBuilder out=new StringBuilder();for(byte x:md.digest())out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static String packageHash(File directory)throws IOException{
        if(Files.isSymbolicLink(directory.toPath()))throw new IOException("Invalid installed driver folder.");
        File[] files=directory.listFiles();if(files==null)throw new IOException("Driver folder is unavailable.");
        Arrays.sort(files,Comparator.comparing(File::getName));
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];
            for(File file:files){
                if(Files.isSymbolicLink(file.toPath())||!file.isFile())throw new IOException("Unexpected entry in the driver folder.");
                md.update(file.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));md.update((byte)0);
                md.update(Long.toString(file.length()).getBytes(java.nio.charset.StandardCharsets.UTF_8));md.update((byte)0);
                try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)md.update(buffer,0,n);}
            }
            StringBuilder result=new StringBuilder();for(byte b:md.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static void discard(File directory)throws IOException{
        if(Files.isSymbolicLink(directory.toPath()))throw new IOException("Unexpected driver staging link.");
        File[] files=directory.listFiles();if(files!=null)for(File file:files){if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath()))discard(file);else Files.deleteIfExists(file.toPath());}Files.deleteIfExists(directory.toPath());
    }
}
