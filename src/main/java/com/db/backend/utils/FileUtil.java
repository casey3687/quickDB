package com.db.backend.utils;

import java.io.File;
import java.io.IOException;

public class FileUtil {
    private FileUtil() {}

    public static void ensureParentDirectory(File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if(parent == null || parent.exists()) {
            return;
        }
        if(parent.mkdirs() || parent.exists()) {
            return;
        }
        Panic.panic(new IOException("Failed to create directory: " + parent.getAbsolutePath()));
    }
}
