package org.cmb.application.domain;

public interface FileStore {

    MockFileDescriptor reserve(String fileName, String contentType);

    MockFileDescriptor put(String fileId, byte[] content);

    MockFileDescriptor getDescriptor(String fileId);

    byte[] getContent(String fileId);

    String downloadUrl(String fileId);

    boolean delete(String fileId);
}
