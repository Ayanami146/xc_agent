package com.xc.agent.service.content;

import com.xc.agent.mapper.ManualDocMapper;
import com.xc.agent.model.po.ManualDocPO;
import com.xc.agent.service.admin.ManualStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualRagManifestServiceTest {

    @Test
    void mapsPublishedMapperRowsToStableInternalContract() {
        ManualDocMapper mapper = mock(ManualDocMapper.class);
        ManualStorageService storage = mock(ManualStorageService.class);
        ManualDocPO doc = ManualDocPO.builder()
                .id(7L).publicId("man_uos").title("UOS 手册").summary("驱动安装")
                .objectKey("0123456789abcdef0123456789abcdef.pdf").fileName("uos.pdf")
                .contentType("application/pdf").sha256("a".repeat(64))
                .versionNo(2).version(4).build();
        when(mapper.selectPublishedForRag()).thenReturn(List.of(doc));
        when(storage.isAvailable(doc.getObjectKey())).thenReturn(true);

        var result = new ManualRagManifestService(mapper, storage).listPublishedManuals();

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.sourceId()).isEqualTo(7L);
            assertThat(item.documentId()).isEqualTo("man_uos");
            assertThat(item.versionNo()).isEqualTo(2);
            assertThat(item.resourceVersion()).isEqualTo(4);
        });
    }

    @Test
    void excludesPublishedRowsWhoseManagedFileIsMissing() {
        ManualDocMapper mapper = mock(ManualDocMapper.class);
        ManualStorageService storage = mock(ManualStorageService.class);
        ManualDocPO legacy = ManualDocPO.builder()
                .id(3L).publicId("manual_3").title("旧种子手册")
                .objectKey("manuals/service-policy-v1.pdf").fileName("service.pdf")
                .contentType("application/pdf").versionNo(1).version(6).build();
        when(mapper.selectPublishedForRag()).thenReturn(List.of(legacy));
        when(storage.isAvailable(legacy.getObjectKey())).thenReturn(false);

        var result = new ManualRagManifestService(mapper, storage).listPublishedManuals();

        assertThat(result).isEmpty();
    }
}
