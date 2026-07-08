package org.apache.hadoop.io.nativeio;

import java.io.File;
import java.io.IOException;

/**
 * Minimal Hadoop NativeIO compatibility shim for local FDL Spark preview.
 *
 * FDL 4.2 bundles Hadoop filesystem classes inside fdl-engine-third-4.2.jar,
 * but this local runtime does not include org.apache.hadoop.io.nativeio.NativeIO.
 * Reporting available lets Hadoop use the Java implementations below instead
 * of falling through to winutils.exe, which is not shipped with this local FDL.
 */
public final class NativeIO {
    private NativeIO() {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static final class Windows {
        private Windows() {
        }

        public enum AccessRight {
            ACCESS_READ,
            ACCESS_WRITE,
            ACCESS_EXECUTE
        }

        public static boolean access(String path, AccessRight right) throws IOException {
            if (path == null) {
                throw new IOException("path is null");
            }
            File file = new File(path);
            if (right == null) {
                return file.exists();
            }
            switch (right) {
                case ACCESS_READ:
                    return file.canRead();
                case ACCESS_WRITE:
                    return file.canWrite();
                case ACCESS_EXECUTE:
                    return file.canExecute();
                default:
                    return file.exists();
            }
        }

        public static void createDirectoryWithMode(File file, int mode) throws IOException {
            if (file == null) {
                throw new IOException("directory is null");
            }
            if (!file.mkdir() && !file.isDirectory()) {
                throw new IOException("Cannot create directory " + file);
            }
        }
    }

    public static final class POSIX {
        private POSIX() {
        }

        public static void chmod(String path, int mode) throws IOException {
            if (path == null) {
                throw new IOException("path is null");
            }
            // No-op by design. This local preview runtime only needs Hadoop's
            // directory creation path to proceed without native chmod support.
        }
    }
}
