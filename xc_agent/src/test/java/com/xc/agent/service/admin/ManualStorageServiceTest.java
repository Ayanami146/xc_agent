package com.xc.agent.service.admin;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.config.AdminProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualStorageServiceTest {
    @TempDir Path directory;

    @Test
    void storesAllowedFileWithDigestAndSafeGeneratedName() throws Exception {
        ManualStorageService service = service();
        var stored = service.store(new MockMultipartFile("file", "../维修手册.pdf",
                "application/pdf", "demo".getBytes()));
        assertThat(stored.fileName()).isEqualTo("维修手册.pdf");
        assertThat(stored.objectKey()).matches("[a-f0-9]{32}\\.pdf");
        assertThat(stored.sha256()).hasSize(64);
        assertThat(service.load(stored.objectKey())).exists();
    }

    @Test
    void rejectsUnsupportedExtensionAndTraversalObjectKey() {
        ManualStorageService service = service();
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "run.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo("MANUAL_FILE_TYPE_UNSUPPORTED");
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "legacy.doc", "application/msword", new byte[]{1})))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo("MANUAL_FILE_TYPE_UNSUPPORTED");
        assertThatThrownBy(() -> service.load("../secret.pdf"))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo("MANUAL_FILE_INVALID");
    }

    private ManualStorageService service() {
        return new ManualStorageService(new AdminProperties("COOKIE", "/auth", 5,
                Duration.ofMinutes(15), directory, 20 * 1024 * 1024));
    }
}
