package service;

import java.awt.Desktop;
import java.io.File;

public class FileService {

    public boolean deleteReal(String path) {
        File f = new File(path);
        if (!f.exists()) return true;
        return f.delete();
    }

    public String renameReal(String oldPath, String newName) {
        File oldFile = new File(oldPath);
        File newFile = new File(oldFile.getParent(), newName);
        if (!oldFile.renameTo(newFile)) return null;
        return newFile.getAbsolutePath();
    }

    public void open(String path) throws Exception {
        Desktop.getDesktop().open(new File(path));
    }
}
