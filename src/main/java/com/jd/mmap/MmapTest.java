package com.jd.mmap;

import sun.misc.Cleaner;
import sun.misc.VM;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MmapTest {
    private static final byte [] bytes = new byte[8192];
    private static final long FILE_SIZE = 128 * 1024 * 1024;
    static {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte ) (i % Byte.MAX_VALUE);
        }
    }

    public static void main(String [] args) {
        long maxDirectMemory = 2L * 1024 * 1024 * 1024;
        if(args.length > 0) {
            maxDirectMemory = toBytes(args[0]);
        }

        if(maxDirectMemory <= 0) {
            System.out.println("Invalid args!");
            System.exit(1);
        }
        long allocateMemory = (long) (maxDirectMemory * 0.8);
        allocateMemory = allocateMemory - allocateMemory % FILE_SIZE;
        int files = (int) (allocateMemory / FILE_SIZE);
        System.out.println(String.format("VM.maxDirectMemory = %s, will create %s * %d = %s。",
                maxDirectMemory, formatTraffic(FILE_SIZE), files, formatTraffic(allocateMemory)));

            File base = new File(System.getProperty("java.io.tmpdir"), "mmap");
            destroyBaseDir(base);
            base.mkdirs();
            System.out.println("Base dir: " + base.getAbsolutePath());

            // 创建文件
            System.out.println("Creating files...");
            File[] fileArray = new File[files];
            ByteBuffer [] buffers = new ByteBuffer[files];
        try {

            for (int i = 0; i < files; i++) {
                fileArray[i] = new File(base, "file_" + i);
                fillFile(fileArray[i], FILE_SIZE);
            }

            System.out.println("Mapping files...");

            for (int i = 0; i < fileArray.length; i++) {
                buffers[i] = load(fileArray[i]);
            }
            System.out.println("Done! press any key to read.");
            System.in.read();

            for (int i = 0; i < buffers.length; i++) {
                while (buffers[i].hasRemaining()) {
                    buffers[i].get(bytes);
                }
            }
            System.out.println("Done! press any key to exit.");
            System.in.read();

        } catch (Throwable t) {
            t.printStackTrace();
            destroyBaseDir(base);
        }
    }


    private static void fillFile(File file, long size) throws IOException {
        try (OutputStream os = new FileOutputStream(file)){
            int w = 0;
            while (w < size) {
                os.write(bytes);
                w += bytes.length;
            }
        }
    }


    private static ByteBuffer load(File file) throws IOException {
        MappedByteBuffer loadBuffer;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel fileChannel = raf.getChannel()) {
            loadBuffer =
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        }
        return loadBuffer;
    }


    private void unload(final Buffer mapped) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (null != mapped) {
            Method getCleanerMethod;
            getCleanerMethod = mapped.getClass().getMethod("cleaner");
            getCleanerMethod.setAccessible(true);
            Cleaner cleaner = (Cleaner) getCleanerMethod.invoke(mapped, new Object[0]);
            cleaner.clean();
        }
    }

    private static String formatTraffic(long size) {
        if (size <= 0) {
            return "0";
        }
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }



    private static void destroyBaseDir(File base) {
        if (base.exists()) {
            if (base.isDirectory()) deleteFolder(base);
            else base.delete();
        }

    }


    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    public static long toBytes(String filesize) {
        long returnValue = -1;
        Pattern patt = Pattern.compile("([\\d.]+)([GMK]B)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = patt.matcher(filesize);
        Map<String, Integer> powerMap = new HashMap<String, Integer>();
        powerMap.put("GB", 3);
        powerMap.put("MB", 2);
        powerMap.put("KB", 1);
        if (matcher.find()) {
            String number = matcher.group(1);
            int pow = powerMap.get(matcher.group(2).toUpperCase());
            BigDecimal bytes = new BigDecimal(number);
            bytes = bytes.multiply(BigDecimal.valueOf(1024).pow(pow));
            returnValue = bytes.longValue();
        }
        return returnValue;
    }
}
