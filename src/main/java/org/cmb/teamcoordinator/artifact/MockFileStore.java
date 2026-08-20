package org.cmb.teamcoordinator.artifact;
import org.cmb.application.domain.FileStore;
import org.cmb.application.domain.MockFileDescriptor;

import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "digital-team.storage", name = "type", havingValue = "memory")
public class MockFileStore implements FileStore {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final Map<String, byte[]> contentById = new ConcurrentHashMap<>();
    private final Map<String, MockFileDescriptor> descriptorById = new ConcurrentHashMap<>();

    public MockFileDescriptor reserve(String fileName, String contentType) {
        String fileId = "mock-file-" + UUID.randomUUID();
        MockFileDescriptor descriptor = new MockFileDescriptor();
        descriptor.setFileId(fileId);
        descriptor.setFileName(fileName);
        descriptor.setContentType(contentType);
        descriptor.setUploadUrl("/mock/files/" + fileId + "/content");
        descriptor.setDownloadUrl("/mock/files/" + fileId + "/content");
        descriptorById.put(fileId, descriptor);
        return descriptor;
    }

    public MockFileDescriptor put(String fileId, byte[] content) {
        MockFileDescriptor descriptor = descriptorById.get(fileId);
        if (descriptor == null) {
            return null;
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Mock file size exceeds 10 MiB.");
        }
        contentById.put(fileId, content.clone());
        descriptor.setSize(content.length);
        descriptor.setChecksum(sha256(content));
        return descriptor;
    }

    public MockFileDescriptor getDescriptor(String fileId) {
        return descriptorById.get(fileId);
    }

    public byte[] getContent(String fileId) {
        byte[] content = contentById.get(fileId);
        return content == null ? null : content.clone();
    }

    @Override
    public String downloadUrl(String fileId) {
        MockFileDescriptor descriptor = descriptorById.get(fileId);
        return descriptor == null ? null : descriptor.getDownloadUrl();
    }

    @Override
    public boolean delete(String fileId) {
        descriptorById.remove(fileId);
        return contentById.remove(fileId) != null;
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder value = new StringBuilder();
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
