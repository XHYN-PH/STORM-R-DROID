/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
import com.naruto.r2.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
public class DriverTest {
 static int checks;
 static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
 interface Work{void run()throws Exception;}
 static void rejects(Work work,String text)throws Exception{try{work.run();throw new AssertionError(text);}catch(IOException expected){checks++;}}
 static byte[] zip(String... names)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){for(String name:names){out.putNextEntry(new ZipEntry(name));out.write(1);out.closeEntry();}}return bytes.toByteArray();}
 static void extract(byte[] bytes,Path dir)throws Exception{DriverArchive.extract(new ByteArrayInputStream(bytes),dir.toFile());}
 public static void main(String[] args)throws Exception{
  check(DriverPolicy.gpuFamily("5143|Turnip Adreno (TM) 750")==DriverPolicy.ADRENO,"Adreno system GPU detection");
  check(DriverPolicy.gpuFamily("13b5|Mali-G57 MC2")==DriverPolicy.MALI,"Mali system GPU detection");
  check(DriverPolicy.gpuFamily("144d|Samsung Xclipse 940")==DriverPolicy.XCLIPSE,"Samsung Xclipse detection");
  check(DriverPolicy.gpuFamily("0000|SwiftShader Device")==DriverPolicy.UNKNOWN,"Software GPUs do not get a hardware driver");
  check(DriverPolicy.packageFamily("PanVK-FristOneRR-V.3.7.0","libvulkan_panfrost.so","")==DriverPolicy.MALI,"Official PanVK package recognition");
  check(DriverPolicy.packageFamily("RADV Xclipse","vulkan.radeon.so","")==DriverPolicy.XCLIPSE,"Official RADV package recognition");
  check(DriverPolicy.packageFamily("Turnip Gen8 V39-patched","vulkan.adreno.so","")==DriverPolicy.ADRENO,"Existing Turnip package recognition");
  check(DriverPolicy.incompatibility(2,"Mali-G57 MC2",2,"PanVK-FristOneRR-V.3.7.0","FristOneRR v3.7.0",30,28).isEmpty(),"Linked PanVK supports its G57 target");
  check(!DriverPolicy.incompatibility(2,"Mali-G710",2,"PanVK-FristOneRR-V.3.7.0","FristOneRR v3.7.0",37,28).isEmpty(),"Known unsupported CSF hardware is blocked for this release");
  check(!DriverPolicy.incompatibility(1,"Adreno 750",2,"PanVK","",37,28).isEmpty(),"Mali packages cannot run on Adreno");
  check(!DriverPolicy.incompatibility(2,"Mali-G57",3,"RADV Xclipse","",37,34).isEmpty(),"Xclipse packages cannot run on Mali");
  check(!DriverPolicy.incompatibility(3,"Xclipse 920",3,"RADV Xclipse","",33,34).isEmpty(),"RADV minimum API is enforced");
  check(DriverPolicy.incompatibility(3,"Xclipse 920",3,"RADV Xclipse","",34,34).isEmpty(),"RADV minimum API permits Android 14");
  check(DriverPolicy.incompatibility(1,"Adreno 750",0,"Legacy package","",30,28).isEmpty(),"Unidentified legacy Adreno packages remain importable");
  check(!DriverPolicy.incompatibility(2,"Mali-G57",0,"Unknown package","",30,28).isEmpty(),"Unidentified non-Adreno packages require identification");
  Path root=Files.createTempDirectory("storm-driver-test-");
  try{
   Path good=Files.createDirectory(root.resolve("good"));extract(zip("folder/meta.json","folder/vulkan.radeon.so","NOTICE.txt"),good);
   check(Files.isRegularFile(good.resolve("meta.json"))&&Files.isRegularFile(good.resolve("NOTICE.txt")),"Legacy flat extraction preserves metadata and notices");
   rejects(()->extract(zip("../outside"),Files.createDirectory(root.resolve("traversal"))),"Traversal ZIP rejected");
   rejects(()->extract(zip("/absolute"),Files.createDirectory(root.resolve("absolute"))),"Absolute ZIP rejected");
   rejects(()->extract(zip("a/meta.json","b/meta.json"),Files.createDirectory(root.resolve("duplicate"))),"Flattened duplicate names rejected");
   rejects(()->extract(zip("directory/"),Files.createDirectory(root.resolve("directory-payload"))),"A directory cannot bypass expanded-file bounds with a hidden payload");
   ByteArrayOutputStream emptyDirectory=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(emptyDirectory)){out.putNextEntry(new ZipEntry("folder/"));out.closeEntry();out.putNextEntry(new ZipEntry("folder/meta.json"));out.write(1);out.closeEntry();}
   Path emptyRoot=Files.createDirectory(root.resolve("empty-directory"));extract(emptyDirectory.toByteArray(),emptyRoot);
   check(Files.isRegularFile(emptyRoot.resolve("meta.json")),"Ordinary empty directory entries remain compatible with flat extraction");
   rejects(()->extract(zip(),Files.createDirectory(root.resolve("empty"))),"Empty ZIP rejected");
   String[] many=new String[129];for(int i=0;i<many.length;i++)many[i]="f"+i;
   rejects(()->extract(zip(many),Files.createDirectory(root.resolve("many"))),"Excessive ZIP entry count rejected");
   byte[] elf=new byte[64];elf[0]=127;elf[1]='E';elf[2]='L';elf[3]='F';elf[4]=2;elf[5]=1;elf[6]=1;elf[16]=3;elf[18]=(byte)183;
   Path library=good.resolve("vulkan.radeon.so");Files.write(library,elf);
   check(DriverArchive.library(good.toFile(),"vulkan.radeon.so").isFile(),"ARM64 shared object accepted");
   String first=DriverArchive.hash(library.toFile()),packageBefore=DriverArchive.packageHash(good.toFile());Files.write(good.resolve("NOTICE.txt"),"updated notice".getBytes("UTF-8"));
   check(!packageBefore.equals(DriverArchive.packageHash(good.toFile()))&&first.equals(DriverArchive.hash(library.toFile())),"Changed notices receive a new package identity even with the same main library");
   packageBefore=DriverArchive.packageHash(good.toFile());Files.write(good.resolve("dependency.so"),new byte[]{1,2,3});
   check(!packageBefore.equals(DriverArchive.packageHash(good.toFile())),"Auxiliary driver dependencies participate in package identity");
   elf[18]=62;Files.write(library,elf);
   rejects(()->DriverArchive.library(good.toFile(),"vulkan.radeon.so"),"x86 driver rejected");
   check(!first.equals(DriverArchive.hash(library.toFile())),"Content identity changes with the driver bytes");
   rejects(()->DriverArchive.library(good.toFile(),"../vulkan.radeon.so"),"Escaping libraryName rejected");
   Files.delete(library);Files.createSymbolicLink(library,root.resolve("outside"));
   rejects(()->DriverArchive.library(good.toFile(),"vulkan.radeon.so"),"Symlink driver rejected");
  }finally{DriverArchive.discard(root.toFile());}
  System.out.println("PASS: "+checks+" driver compatibility/archive assertions");
 }
}
