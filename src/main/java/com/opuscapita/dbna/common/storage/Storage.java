package com.opuscapita.dbna.common.storage;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.InputStream;
import java.util.List;

@SuppressWarnings("unused")
public interface Storage {

    /**
     * Returns the file content as input stream.
     *
     * @param path full path of the file ex: "/peppol/hot/20190223/test.xml"
     * @return file content
     * @throws StorageException storage exception
     */
    @Retryable(value = {Exception.class}, maxAttempts = 5, backoff = @Backoff(delay = 3000))
    InputStream get(String path) throws StorageException;

    /**
     * Returns the file size as bytes.
     *
     * @param path full path of the file ex: "/peppol/hot/20190223/test.xml"
     * @return file size
     * @throws StorageException storage exception
     */
    Long size(String path) throws StorageException;

    /**
     * Checks the folder and returns full paths of files in that folder.
     *
     * @param folder folder name, has to end with slash ex: "/peppol/in/a2a/"
     * @return path list
     * @throws StorageException storage exception
     */
    List<String> list(String folder) throws StorageException;

    /**
     * Updates file content in the path with the given content.
     *
     * @param content updated file content as input stream
     * @param path full path of the file ex: "/peppol/cold/9908_987987987/0007_232100032/20199223/test.xml"
     * @throws StorageException storage exception
     */
    void update(InputStream content, String path) throws StorageException;

    /**
     * Puts file to the given folder with the given filename.
     *
     * @param content file content as input stream
     * @param folder folder to put the file, has to end with slash ex: "/peppol/in/xib/"
     * @param filename filename ex: "test.xml"
     * @return final full path of the file ex: "/peppol/in/xib/test.xml"
     * @throws StorageException storage exception
     */
    @Retryable(value = {Exception.class}, maxAttempts = 5, backoff = @Backoff(delay = 3000))
    String put(InputStream content, String folder, String filename) throws StorageException;

    /**
     * Moves file to the given folder.
     *
     * @param path current full path of the file ex: "/peppol/cold/9908_987987987/0007_232100032/20199223/test.xml"
     * @param folder destination folder, has to end with slash ex: "/peppol/out/xib/"
     * @return final full path of the file ex: "/peppol/out/xib/test.xml"
     * @throws StorageException storage exception
     */
    @Retryable(value = {Exception.class}, maxAttempts = 5, backoff = @Backoff(delay = 3000))
    String move(String path, String folder) throws StorageException;

    /**
     * Removes file or directory recursively.
     *
     * @param path full path of file or directory ex: "/peppol/hot/20199223/test.xml"
     * @throws StorageException storage exception
     */
    void remove(String path) throws StorageException;

    /**
     * Checks if a file exists in the given path.
     *
     * @param path full path of the file ex: "/peppol/hot/20199223/test.xml"
     * @return true if file exists
     * @throws StorageException storage exception
     */
    boolean exists(String path) throws StorageException;
}

