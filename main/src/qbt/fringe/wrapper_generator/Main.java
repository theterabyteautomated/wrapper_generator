package qbt.fringe.wrapper_generator;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {
    public static void main(String[] args) throws IOException {
        if(args.length < 2 || args.length % 2 != 0) {
            System.out.println("Usage: wrapper-generator <input directory> <output directory> (<wrapper> <main class>)*");
            System.exit(1);
        }
        File inputDirectory = new File(args[0]);
        File outputDirectory = new File(args[1]);
        File libDirectory = new File(outputDirectory, "lib");
        File binDirectory = new File(outputDirectory, "bin");
        libDirectory.mkdirs();
        binDirectory.mkdirs();
        Map<String, Map<Long, List<String>>> classToCrcToJars = Maps.newHashMap();
        for(File packageDir : inputDirectory.listFiles()) {
            if(packageDir.getName().startsWith(".")) {
                continue;
            }
            File jarsDir = new File(packageDir, "jars");
            if(!jarsDir.isDirectory()) {
                continue;
            }
            for(File jar : jarsDir.listFiles()) {
                String jarName = jar.getName();
                if(jar.isFile() && jarName.endsWith(".jar")) {
                    try(ZipFile zf = new ZipFile(jar)) {
                        Enumeration<? extends ZipEntry> en = zf.entries();
                        while(en.hasMoreElements()) {
                            ZipEntry ze = en.nextElement();
                            if(ze.getName().endsWith(".class")) {
                                long crc = ze.getCrc();
                                Map<Long, List<String>> crcToJars = classToCrcToJars.get(ze.getName());
                                if(crcToJars == null) {
                                    classToCrcToJars.put(ze.getName(), crcToJars = Maps.newHashMap());
                                }
                                List<String> jars = crcToJars.get(crc);
                                if(jars == null) {
                                    crcToJars.put(crc, jars = Lists.newLinkedList());
                                }
                                jars.add(jarName);
                            }
                        }
                    }
                    File destination = new File(libDirectory, jar.getName());
                    Files.copy(jar.toPath(), destination.toPath());
                }
            }
        }
        // It's deeply insane to have multiple jars provide the same class but,
        // if for some dumb-ass reason they provide the same file (or at least
        // CRC) we look the other way.  However, if they are different class
        // contents (or at least CRC) we're putting our foot down.
        for(Map.Entry<String, Map<Long, List<String>>> e : classToCrcToJars.entrySet()) {
            if(e.getValue().size() > 1) {
                throw new IllegalArgumentException("Classfile collision at " + e.getKey() + ": " + e.getValue());
            }
        }
        List<String> template = Resources.asCharSource(Main.class.getResource("wrapper_template.py.txt"), Charsets.UTF_8).readLines();
        for(int i = 2; i < args.length; i += 2) {
            String wrapper = args[i];
            String clazz = args[i + 1];
            File wrapperFile = new File(binDirectory, wrapper);
            try(FileOutputStream fos = new FileOutputStream(wrapperFile);
                    PrintWriter pw = new PrintWriter(fos)) {
                for(String line : template) {
                    pw.println(line.replace("%(clazz)s", clazz));
                }
            }
            wrapperFile.setExecutable(true);
        }
    }
}
