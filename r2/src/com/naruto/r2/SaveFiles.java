/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
package com.naruto.r2;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Isolated save operations. Never writes the upstream 100% save or its markers. */
public final class SaveFiles implements AutoCloseable {
    private final File base,backups;
    private final RandomAccessFile lockFile;
    private final FileLock lock;
    public SaveFiles(File directory) throws IOException {this(directory,directory);}
    public SaveFiles(File directory,File lockDirectory) throws IOException {
        base=directory.getCanonicalFile();backups=new File(base,"save-backups");
        rootSafe(backups);rootSafe(new File(base,"data_revolution/content"));
        if(Files.isSymbolicLink(new File(lockDirectory,"r2-game.lock").toPath()))throw new IOException("Invalid game-lock path.");
        lockFile=new RandomAccessFile(new File(lockDirectory,"r2-game.lock"),"rw");
        FileLock acquired=null;
        try { acquired=lockFile.getChannel().tryLock(); } catch(OverlappingFileLockException busy) {} catch(IOException e){lockFile.close();throw e;}
        if(acquired==null){lockFile.close();throw new IOException("Close the game before managing saves.");}
        lock=acquired;
    }
    @Override public void close() throws IOException {try{lock.release();}finally{lockFile.close();}}
    public File current() throws IOException {
        File content=new File(base,"data_revolution/content");File[] files=content.listFiles();
        if(files!=null)for(File f:files)if(f.isDirectory()&&f.getName().equalsIgnoreCase("STORMR.S"))return safe(content,f);
        return new File(content,"stormr.s");
    }
    private static void rootSafe(File root) throws IOException {
        if(!root.getCanonicalFile().toPath().equals(root.getAbsoluteFile().toPath().normalize()))throw new IOException("Symbolic links are not supported in save folders.");
    }
    private static File safe(File root,File file) throws IOException {
        rootSafe(root);
        // Reject symlinks even when they point inside the allowed tree.
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Symbolic links are not supported in saves.");
        File canonical=file.getCanonicalFile();
        if(!canonical.toPath().startsWith(root.getCanonicalFile().toPath())||canonical.equals(root.getCanonicalFile()))
            throw new IOException("Invalid save path.");
        return canonical;
    }
    public File[] list() throws IOException {
        File[] values=backups.listFiles(File::isDirectory);if(values==null)return new File[0];
        ArrayList<File> result=new ArrayList<>();
        for(File f:values)if(!f.getName().startsWith(".")){safe(backups,f);result.add(f);}
        result.sort((a,b)->b.getName().compareTo(a.getName()));return result.toArray(new File[0]);
    }
    private File selected(File backup) throws IOException {
        File f=safe(backups,backup);
        if(!f.getParentFile().equals(backups.getCanonicalFile())||!f.isDirectory()||f.getName().startsWith("."))throw new IOException("Invalid backup.");
        return f;
    }
    private File payload(File backup) throws IOException {
        File[] files=selected(backup).listFiles();
        if(files!=null)for(File f:files)if(f.isDirectory()&&f.getName().equalsIgnoreCase("STORMR.S"))return safe(backup,f);
        throw new IOException("This backup does not contain a STORM R save.");
    }
    public void exportCurrent(OutputStream out) throws IOException {exportDirectory(current(),out);}
    public void exportBackup(File backup,OutputStream out) throws IOException {exportDirectory(payload(backup),out);}
    private void exportDirectory(File save,OutputStream output) throws IOException {
        if(!save.isDirectory())throw new IOException("No save was found.");
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            zipTree(save,save,"save/",zip);
            File name=new File(save.getParentFile(),save.getName()+".name");
            if(name.isFile()){safe(save.getParentFile(),name);zip.putNextEntry(new ZipEntry("save.name"));copy(name,zip);zip.closeEntry();}
        }
    }
    private static void zipTree(File root,File file,String prefix,ZipOutputStream zip) throws IOException {
        File[] children=file.listFiles();if(children==null)throw new IOException("Cannot read save directory.");
        Arrays.sort(children,Comparator.comparing(File::getName));
        for(File child:children){safe(root,child);String entry=prefix+child.getName();
            if(child.isDirectory())zipTree(root,child,entry+"/",zip);
            else if(child.isFile()){zip.putNextEntry(new ZipEntry(entry));copy(child,zip);zip.closeEntry();}
        }
    }
    private static void copy(File source,OutputStream out) throws IOException {
        try(InputStream in=new FileInputStream(source)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    }
    private static void copyTree(File root,File src,File dst) throws IOException {
        safe(root,src);
        if(src.isDirectory()){
            if(!dst.isDirectory()&&!dst.mkdirs())throw new IOException("Cannot create backup directory.");
            File[] children=src.listFiles();if(children==null)throw new IOException("Cannot read save directory.");
            for(File child:children)copyTree(root,child,new File(dst,child.getName()));
        }else{
            if(!dst.getParentFile().isDirectory()&&!dst.getParentFile().mkdirs())throw new IOException("Cannot create save directory.");
            try(FileOutputStream out=new FileOutputStream(dst)){copy(src,out);out.getFD().sync();}
        }
    }
    public File backupCurrent() throws IOException {
        File src=current();if(!src.isDirectory())throw new IOException("No save was found.");
        if(!backups.isDirectory()&&!backups.mkdirs())throw new IOException("Cannot create backup folder.");
        String id="R2-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8);
        File stage=new File(backups,"."+id),ready=new File(backups,id);
        if(!stage.mkdir())throw new IOException("Cannot stage backup.");
        try{
            copyTree(src.getParentFile(),src,new File(stage,src.getName()));
            File name=new File(src.getParentFile(),src.getName()+".name");
            if(name.isFile())copyTree(src.getParentFile(),name,new File(stage,name.getName()));
            if(!stage.renameTo(ready))throw new IOException("Cannot finish backup.");
            return ready;
        }catch(IOException e){deleteTree(backups,stage);throw e;}
    }
    public void restore(File backup) throws IOException {
        if(new File(base,"save100-revolution.active").isFile())throw new IOException("The 100% save is active. Turn it off in the existing Save menu before restoring a backup.");
        File source=payload(backup),target=current(),parent=target.getParentFile();
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create save folder.");
        File stage=new File(parent,".r2-stage-"+UUID.randomUUID());
        File previous=new File(parent,".r2-previous-"+UUID.randomUUID());
        File name=new File(parent,target.getName()+".name");
        boolean moved=false;
        try{
            copyTree(source.getParentFile(),source,stage);
            if(target.isDirectory()){
                backupCurrent();
                if(!target.renameTo(previous))throw new IOException("Cannot preserve the current save.");
                moved=true;
            }
            if(!stage.renameTo(target))throw new IOException("Cannot install the restored save.");
            File oldName=new File(source.getParentFile(),source.getName()+".name");
            if(oldName.isFile()){
                File temp=new File(parent,".r2-name-"+UUID.randomUUID());
                copyTree(source.getParentFile(),oldName,temp);
                Files.move(temp.toPath(),name.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }
            // Keep the old directory as an additional recovery copy; never delete it here.
        }catch(IOException error){
            if(moved&&!target.exists()&&!previous.renameTo(target))error.addSuppressed(new IOException("Recovery copy retained at "+previous.getName()));
            throw error;
        }finally{if(stage.exists())deleteTree(parent,stage);}
    }
    public void deleteBackup(File backup) throws IOException {deleteTree(backups,selected(backup));}
    private static void deleteTree(File root,File path) throws IOException {
        safe(root,path);
        if(path.isDirectory()){File[] files=path.listFiles();if(files==null)throw new IOException("Cannot read backup.");for(File f:files)deleteTree(root,f);}
        if(path.exists()&&!path.delete())throw new IOException("Cannot delete backup.");
    }
}
