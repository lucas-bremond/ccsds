package eu.dariolucia.ccsds.cfdp.filestore.impl;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A basic filestore, composed of a flat list of directories, each containing a file.
 * A full path is composed by the format &lt;directory name&gt;/&lt;file name&gt;. &lt;directory name&gt; can be omitted
 * if the file resides in the root folder of the virtual file store.
 */
public class FilesystemBasedFilestore implements IVirtualFilestore {

    public static final String DIR_FILE_SEPARATOR = "/";
    private final File root;

    public FilesystemBasedFilestore(String absoluteRootPath) {
        this(new File(absoluteRootPath));
    }

    public FilesystemBasedFilestore(File root) {
        if(root == null) {
            throw new NullPointerException("Provided file is null");
        }
        if(!root.exists()) {
            throw new IllegalArgumentException(String.format("Provided file %s does not exist", root.getAbsolutePath()));
        }
        if(!root.isDirectory()) {
            throw new IllegalArgumentException(String.format("Provided file %s is not a directory", root.getAbsolutePath()));
        }
        this.root = root;
    }

    @Override
    public void createFile(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            try {
                if(!target.createNewFile()) {
                    throw new FilestoreException(String.format("Cannot create file %s: createNewFile() returned false", fullPath));
                }
            } catch (IOException e) {
                throw new FilestoreException(String.format("Cannot create file %s: %s", fullPath, e.getMessage()), e);
            }
        } else if(target.isDirectory()) {
            throw new FilestoreException(String.format("Cannot create file %s: directory with the same name exists", fullPath));
        }
    }

    @Override
    public void deleteFile(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(target.exists() && target.isFile()) {
            try {
                Files.delete(target.toPath());
            } catch (IOException e) {
                throw new FilestoreException(String.format("Cannot delete file %s: %s", fullPath, e.getMessage()), e);
            }
        } else if(target.isDirectory()) {
            throw new FilestoreException(String.format("Cannot delete file %s: the file is a directory", fullPath));
        } else {
            throw new FilestoreException(String.format("Cannot delete file %s: the file does not exist", fullPath));
        }
    }

    @Override
    public void renameFile(String fullPath, String newFullPath) throws FilestoreException {
        File source = constructTarget(fullPath);
        File target = constructTarget(newFullPath);
        if(target.exists()) {
            throw new FilestoreException(String.format("Cannot move file %s to %s: destination file already exists", fullPath, newFullPath));
        }
        if(source.exists()) {
            try {
                Files.move(source.toPath(), target.toPath());
            } catch (IOException e) {
                throw new FilestoreException(String.format("Cannot move file %s to %s: %s", fullPath, newFullPath, e.getMessage()), e);
            }
        } else {
            throw new FilestoreException(String.format("Cannot move file %s to %s: source file does not exist", fullPath, newFullPath));
        }
    }

    @Override
    public void appendFile(String fullPath, byte[] data) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            throw new FilestoreException(String.format("Cannot append to file %s: destination file does not exist", fullPath));
        }
        try {
            try (FileOutputStream fos = new FileOutputStream(target, true)) {
                fos.write(data);
            }
        } catch (IOException e) {
            throw new FilestoreException(String.format("Cannot append to file %s: %s", fullPath, e.getMessage()), e);
        }
    }

    @Override
    public void replaceFile(String fullPath, byte[] data) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            throw new FilestoreException(String.format("Cannot replace file %s: destination file does not exist", fullPath));
        }
        try {
            try (FileOutputStream fos = new FileOutputStream(target, false)) {
                fos.write(data);
            }
        } catch (IOException e) {
            throw new FilestoreException(String.format("Cannot replace file %s: %s", fullPath, e.getMessage()), e);
        }
    }

    @Override
    public byte[] getFile(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            throw new FilestoreException(String.format("Cannot read file %s: file does not exist", fullPath));
        }
        try {
            return Files.readAllBytes(target.toPath());
        } catch (IOException e) {
            throw new FilestoreException(String.format("Cannot read file %s: %s", fullPath, e.getMessage()), e);
        }
    }

    @Override
    public void createDirectory(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            if(!target.mkdir()) {
                throw new FilestoreException(String.format("Cannot create directory %s: createDirectory() returned false", fullPath));
            }
        } else if(!target.isDirectory()) {
            throw new FilestoreException(String.format("Cannot create directory %s: file with the same name exists", fullPath));
        }
    }

    @Override
    public void deleteDirectory(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(target.exists() && target.isDirectory()) {
            try {
                Files.delete(target.toPath());
            } catch (IOException e) {
                throw new FilestoreException(String.format("Cannot delete directory %s: %s", fullPath, e.getMessage()), e);
            }
        } else if(!target.isDirectory()) {
            throw new FilestoreException(String.format("Cannot delete directory %s: the path is not a directory", fullPath));
        } else {
            throw new FilestoreException(String.format("Cannot directory file %s: the directory does not exist", fullPath));
        }
    }

    @Override
    public List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException {
        File target = constructTarget(fullPath);
        try {
            return Files.walk(target.toPath(), recursive ? Integer.MAX_VALUE : 1).map(Path::toFile).filter(f -> !f.getAbsolutePath().equals(target.getAbsolutePath())).map(this::fileToName).collect(Collectors.toList()); // NOSONAR pointless remark
        } catch (IOException e) {
            throw new FilestoreException(String.format("Cannot list directory %s: %s", fullPath, e.getMessage()), e);
        }
    }

    private String fileToName(File file) {
        String fullPath = file.getAbsolutePath().substring(root.getAbsolutePath().length());
        String matcher = File.separator;
        // Handle Windows separator
        if(matcher.equals("\\")) {
            matcher = "\\\\";
        }
        fullPath = fullPath.replaceAll(matcher, DIR_FILE_SEPARATOR);
        if(fullPath.startsWith(DIR_FILE_SEPARATOR)) {
            fullPath = fullPath.substring(1);
        }
        if(file.isDirectory() && !fullPath.endsWith(DIR_FILE_SEPARATOR)) {
            fullPath += DIR_FILE_SEPARATOR;
        }
        return fullPath;
    }

    @Override
    public boolean fileExists(String fullPath) {
        File target = constructTarget(fullPath);
        return target.exists() && target.isFile();
    }

    @Override
    public boolean directoryExists(String fullPath) {
        File target = constructTarget(fullPath);
        return target.exists() && target.isDirectory();
    }

    @Override
    public long fileSize(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            throw new FilestoreException(String.format("Cannot read size of file %s: file does not exist", fullPath));
        } else {
            try {
                return Files.size(target.toPath());
            } catch (IOException e) {
                throw new FilestoreException(e);
            }
        }
    }

    @Override
    public boolean isUnboundedFile(String sourceFileName) {
        return false;
    }

    @Override
    public InputStream readFile(String fullPath) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            throw new FilestoreException(String.format("Cannot read file %s: file does not exist", fullPath));
        } else {
            try {
                return new FileInputStream(target);
            } catch (FileNotFoundException e) {
                throw new FilestoreException(e);
            }
        }
    }

    @Override
    public OutputStream writeFile(String fullPath, boolean append) throws FilestoreException {
        File target = constructTarget(fullPath);
        if(!target.exists()) {
            throw new FilestoreException(String.format("Cannot write to file %s: file does not exist", fullPath));
        }
        try {
            return new FileOutputStream(target, append);
        } catch (IOException e) {
            throw new FilestoreException(String.format("Cannot append to file %s: %s", fullPath, e.getMessage()), e);
        }
    }

    private File constructTarget(String fullPath) {
        String[] splt = fullPath.split(DIR_FILE_SEPARATOR, -1);
        if(splt.length == 2) {
            return new File(root.getAbsolutePath() + (splt[0] != null ? File.separator + splt[0] : "") + File.separator + splt[1]);
        } else {
            return new File(root.getAbsolutePath() + File.separator + splt[0]);
        }
    }
}
