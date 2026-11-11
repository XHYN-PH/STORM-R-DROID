/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
import com.naruto.r2.SaveFiles;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public final class SaveFilesTest {
    private static int checks;
    static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    static void write(File f,String text)throws Exception{f.getParentFile().mkdirs();Files.write(f.toPath(),text.getBytes("UTF-8"));}
    static String read(File f)throws Exception{return new String(Files.readAllBytes(f.toPath()),"UTF-8");}
    interface Task {void run()throws Exception;}
    static void blocked(Task task,String message)throws Exception{boolean failed=false;try{task.run();}catch(IOException e){failed=true;}check(failed,message);}
    public static void main(String[] args)throws Exception {
        File base=Files.createTempDirectory("storm-r2-save-test-").toFile();
        File save=new File(base,"data_revolution/content/stormr.s"),data=new File(save,"STORMR.S");write(data,"active-progress");
        File flag=new File(base,"save100-revolution.active"),original=new File(base,"save100-original-revolution/package/STORMR.S");
        write(flag,"active-flag");write(original,"original-save-backup");
        File asset=new File(base,"bundled-assets/save100/STORMR.S");write(asset,"bundled-100-percent");
        try(SaveFiles files=new SaveFiles(base)){
            ByteArrayOutputStream output=new ByteArrayOutputStream();files.exportCurrent(output);
            try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))){ZipEntry entry=in.getNextEntry();check("save/STORMR.S".equals(entry.getName()),"Export is reimportable by the upstream basename-based importer");check(new String(in.readAllBytes(),"UTF-8").equals("active-progress"),"Export bytes match active save");}
            check(read(data).equals("active-progress"),"Export does not change active save");
            check(read(flag).equals("active-flag"),"Export preserves 100% activation flag");
            check(read(original).equals("original-save-backup"),"Export preserves original-save backup");
            File backup=files.backupCurrent();check(files.list().length==1,"Backup appears in manager");
            blocked(()->files.restore(backup),"Restore is blocked while 100% save is active");
            check(read(data).equals("active-progress"),"Blocked restore preserves active save bytes");
            check(read(flag).equals("active-flag"),"Blocked restore preserves activation flag");
            check(read(original).equals("original-save-backup"),"Blocked restore preserves original-save backup");
            blocked(()->files.deleteBackup(save),"Cannot delete active save through backup manager");
            blocked(()->files.deleteBackup(new File(base,"save100-original-revolution")),"Cannot delete 100% original backup");
            blocked(()->files.deleteBackup(new File(base,"save-backups/../save100-original-revolution")),"Traversal cannot escape backup root");
            blocked(()->new SaveFiles(base).close(),"Second process/operation lock is denied");
            check(flag.delete(),"Fixture disables 100% save outside SaveFiles");
            write(data,"newer-progress");files.restore(backup);
            check(read(data).equals("active-progress"),"Restore installs selected backup");
            check(files.list().length==2,"Restore preserves current progress as a new backup");
            check(!flag.exists(),"Restore does not activate/deactivate 100% save");
            check(read(original).equals("original-save-backup"),"Restore never changes original-save backup");
            check(read(asset).equals("bundled-100-percent"),"Bundled save fixture remains unchanged");
            ByteArrayOutputStream exported=new ByteArrayOutputStream();files.exportBackup(backup,exported);check(exported.size()>0,"Backup export works");
            files.deleteBackup(backup);check(!backup.exists(),"Only selected backup is deleted");
            check(read(data).equals("active-progress"),"Deleting a backup preserves active progress");
            File link=new File(save,"linked");Files.createSymbolicLink(link.toPath(),original.toPath());
            blocked(()->files.exportCurrent(new ByteArrayOutputStream()),"Export refuses symlinks into protected saves");
            Files.delete(link.toPath());
            File externalLink=new File(base,"save-backups/escape");Files.createSymbolicLink(externalLink.toPath(),new File(base,"save100-original-revolution").toPath());
            blocked(()->files.deleteBackup(externalLink),"Deletion refuses linked backup paths");Files.delete(externalLink.toPath());
        }
        try(SaveFiles again=new SaveFiles(base)){check(again.list().length==1,"Lock releases cleanly after operation");}
        File rootLink=Files.createTempDirectory("storm-r2-linked-backups-").toFile();
        File protectedSave=new File(rootLink,"save100-original-revolution");protectedSave.mkdirs();write(new File(protectedSave,"STORMR.S"),"protected");
        Files.createSymbolicLink(new File(rootLink,"save-backups").toPath(),protectedSave.toPath());
        blocked(()->new SaveFiles(rootLink).close(),"Linked backup root is rejected before any operation");
        check(read(new File(protectedSave,"STORMR.S")).equals("protected"),"Linked protected 100% backup remains intact");
        System.out.println("PASS: "+checks+" save regression assertions; isolated fixture "+base);
    }
}
