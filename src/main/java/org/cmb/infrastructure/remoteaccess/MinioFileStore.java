package org.cmb.infrastructure.remoteaccess;
import org.cmb.application.domain.FileStore;
import org.cmb.application.domain.MockFileDescriptor;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.cmb.common.config.DigitalTeamProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@ConditionalOnProperty(prefix = "digital-team.storage", name = "type", havingValue = "minio", matchIfMissing = true)
public class MinioFileStore implements FileStore {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final MinioClient client;
    private final String bucket;
    private final Map<String, MockFileDescriptor> descriptorById = new ConcurrentHashMap<>();

    public MinioFileStore(DigitalTeamProperties properties) {
        DigitalTeamProperties.Storage storage = properties.getStorage();
        this.bucket = storage.getBucket();
        this.client = MinioClient.builder()
                .endpoint(storage.getEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey())
                .build();
    }

    @PostConstruct
    public void initializeBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    public MockFileDescriptor reserve(String fileName, String contentType) {
        try {
            String fileId = "file-" + UUID.randomUUID();
            MockFileDescriptor descriptor = new MockFileDescriptor();
            descriptor.setFileId(fileId);
            descriptor.setFileName(fileName);
            descriptor.setContentType(contentType);
            descriptor.setUploadUrl(presign(fileId, Method.PUT));
            descriptor.setDownloadUrl(presign(fileId, Method.GET));
            descriptorById.put(fileId, descriptor);
            return descriptor;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reserve a MinIO object.", ex);
        }
    }

    @Override
    public MockFileDescriptor put(String fileId, byte[] content) {
        MockFileDescriptor descriptor = descriptorById.get(fileId);
        if (descriptor == null) {
            return null;
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10 MiB.");
        }
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileId)
                    .contentType(descriptor.getContentType())
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
            descriptor.setSize(content.length);
            descriptor.setChecksum(sha256(content));
            return descriptor;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not upload object to MinIO.", ex);
        }
    }

    @Override
    public MockFileDescriptor getDescriptor(String fileId) {
        return descriptorById.get(fileId);
    }

    @Override
    public byte[] getContent(String fileId) {
        try {
            return StreamUtils.copyToByteArray(client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(fileId).build()));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not download object from MinIO.", ex);
        }
    }

    @Override
    public String downloadUrl(String fileId) {
        try {
            return presign(fileId, Method.GET);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign MinIO download.", ex);
        }
    }

    @Override
    public boolean delete(String fileId) {
        if (descriptorById.remove(fileId) == null) {
            return false;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(fileId).build());
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not delete object from MinIO.", ex);
        }
    }

    private String presign(String fileId, Method method) throws Exception {
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(fileId)
                .method(method)
                .expiry(1, TimeUnit.HOURS)
                .build());
    }

    private String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder value = new StringBuilder();
        for (byte item : digest) {
            value.append(String.format("%02x", item));
        }
        return value.toString();
    }
}
