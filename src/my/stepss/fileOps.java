/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package my.stepss;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author p3tris
 */
public class fileOps {

    public static boolean deleteDirectory(File directory) {

        // System.out.println("removeDirectory " + directory);

        if (directory == null) {
            return false;
        }
        if (!directory.exists()) {
            return true;
        }
        if (!directory.isDirectory()) {
            return false;
        }

        String[] list = directory.list();

        // Some JVMs return null for File.list() when the
        // directory is empty.
        if (list != null) {
            for (int i = 0; i < list.length; i++) {
                File entry = new File(directory, list[i]);

                //        System.out.println("\tremoving entry " + entry);

                if (entry.isDirectory()) {
                    if (!deleteDirectory(entry)) {
                        return false;
                    }
                } else {
                    if (!entry.delete()) {
                        return false;
                    }
                }
            }
        }

        return directory.delete();
    }

    public static void extractToFolder(ZipInputStream zin, File outputFolderRoot)
            throws IOException {

        FileOutputStream fos = null;
        byte[] buf = new byte[1024];
        ZipEntry zipentry;

        for (zipentry = zin.getNextEntry(); zipentry != null; zipentry = zin.getNextEntry()) {

            try {
                String entryName = zipentry.getName();
                int n;

                File newFile = new File(outputFolderRoot, entryName);
                if (zipentry.isDirectory()) {
                    newFile.mkdirs();
                    continue;
                } else {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                }

                fos = new FileOutputStream(newFile);

                while ((n = zin.read(buf, 0, 1024)) > -1) {
                    fos.write(buf, 0, n);
                }

                fos.close();
                zin.closeEntry();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception ignore) {
                    }
                }
            }

        }

        zin.close();

    }

    public static void copyFiletoDir(File srcFile, File dstDir) throws IOException {
        if (srcFile.exists()) {
            FileInputStream streamIn = new FileInputStream(srcFile);
            File dstFile = new File(dstDir.getParent() + System.getProperty("file.separator") + srcFile.getName());
            OutputStream streamOut = FileUtils.openOutputStream(dstFile);
            IOUtils.copy(streamIn, streamOut);
            streamIn.close();
            streamOut.close();
        }
    }

    public static void copyFiletoFile(File srcFile, File dstFile) throws IOException {
        if (srcFile.exists()) {
            FileInputStream streamIn = new FileInputStream(srcFile);
            OutputStream streamOut = FileUtils.openOutputStream(dstFile);
            IOUtils.copy(streamIn, streamOut);
            streamIn.close();
            streamOut.close();
        }
    }
}
