/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
import com.naruto.r2.SessionFiles;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;

public final class SessionFilesTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("storm-session-test-");Path status=root.resolve("session.txt");
        SessionFiles.write(status,"old session data");SessionFiles.write(status,"new");
        check(SessionFiles.read(status,100,false).equals("new"),"Snapshots replace the previous session without retaining history");
        try(java.util.stream.Stream<Path> files=Files.list(root)){check(files.count()==1,"Atomic writes leave no temporary files");}
        SessionFiles.write(status,"0123456789");
        check(SessionFiles.read(status,4,false).equals("0123"),"Head reads are bounded");
        check(SessionFiles.read(status,4,true).equals("6789"),"Tail reads are bounded");
        check(SessionFiles.read(root.resolve("missing"),10,false).isEmpty(),"Missing optional metadata is harmless");
        Path link=root.resolve("linked");Files.createSymbolicLink(link,status);
        check(SessionFiles.read(link,100,false).isEmpty(),"Status reads do not follow symlinks");
        try{SessionFiles.read(status,SessionFiles.MAX_READ+1,false);throw new AssertionError("Accepted unbounded read");}catch(IllegalArgumentException expected){checks++;}
        byte[] excerpt=new byte[SessionFiles.MAX_READ+16];java.util.Arrays.fill(excerpt,(byte)'x');excerpt[excerpt.length-1]='Z';Files.write(status,excerpt);
        String bounded=SessionFiles.read(status,SessionFiles.MAX_READ,true);
        check(bounded.length()==SessionFiles.MAX_READ&&bounded.endsWith("Z"),"The renderer's 1 MiB excerpt is accepted and remains bounded");
        check(SessionFiles.gameIdle(root),"Idle probe acquires and releases only the dedicated game lock");
        try(RandomAccessFile file=new RandomAccessFile(root.resolve("r2-game.lock").toFile(),"rw");FileLock lock=file.getChannel().lock()){
            check(!SessionFiles.gameIdle(root),"An existing game/save lock keeps the closing dialog waiting");
            check(lock.isValid(),"A status probe cannot release the engine's lock");
        }
        check(SessionFiles.gameIdle(root),"Shutdown becomes complete only after the owner releases its lock");
        check(Files.size(root.resolve("r2-game.lock"))==0,"The status probe writes no game/save data");
        SessionFiles.write(status,"Closing game…");check(SessionFiles.read(status,100,false).equals("Closing game…"),"Status text preserves UTF-8");
        System.out.println("PASS: "+checks+" session/status file assertions");
    }
}
