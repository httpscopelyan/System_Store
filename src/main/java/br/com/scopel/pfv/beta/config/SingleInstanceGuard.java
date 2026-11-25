package br.com.scopel.pfv.beta.config;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;

public final class SingleInstanceGuard {

    private static RandomAccessFile heldFile;
    private static FileLock heldLock;

    private SingleInstanceGuard() {
    }

    public static boolean acquire() {
        return acquire(Path.of(System.getProperty("java.io.tmpdir"), "scopel-store.instance.lock"));
    }

    public static synchronized boolean acquire(Path lockFile) {
        if (heldLock != null) {
            return false;
        }
        try {
            RandomAccessFile file = new RandomAccessFile(lockFile.toFile(), "rw");
            FileChannel channel = file.getChannel();
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException alreadyHeldInThisJvm) {
                file.close();
                return false;
            }
            if (lock == null) {
                file.close();
                return false;
            }
            heldFile = file;
            heldLock = lock;
            return true;
        } catch (IOException unavailable) {
            return true;
        }
    }

    public static synchronized void release() {
        try {
            if (heldLock != null) {
                heldLock.release();
            }
            if (heldFile != null) {
                heldFile.close();
            }
        } catch (IOException ignored) {
            return;
        } finally {
            heldLock = null;
            heldFile = null;
        }
    }
}
