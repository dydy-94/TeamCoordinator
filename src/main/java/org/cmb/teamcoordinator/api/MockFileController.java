package org.cmb.teamcoordinator.api;

import java.util.Map;
import org.cmb.application.domain.MockFileDescriptor;
import org.cmb.application.domain.FileStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/files")
public class MockFileController {

    private final FileStore fileStore;

    public MockFileController(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @PostMapping("/presign")
    public MockFileDescriptor presign(@RequestBody Map<String, String> request) {
        return fileStore.reserve(request.get("fileName"), request.get("contentType"));
    }

    @PutMapping("/{fileId}/content")
    public ResponseEntity<MockFileDescriptor> upload(@PathVariable String fileId, @RequestBody byte[] content) {
        MockFileDescriptor descriptor = fileStore.put(fileId, content);
        return descriptor == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(descriptor);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<MockFileDescriptor> metadata(@PathVariable String fileId) {
        MockFileDescriptor descriptor = fileStore.getDescriptor(fileId);
        return descriptor == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(descriptor);
    }

    @GetMapping(value = "/{fileId}/content", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable String fileId) {
        byte[] content = fileStore.getContent(fileId);
        return content == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(content);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        return fileStore.delete(fileId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
