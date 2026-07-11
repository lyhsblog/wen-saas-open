package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FilePermissionChecker;
import com.wensaas.core.api.file.FileVisibility;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilePermissionEvaluatorTest {

    private FileMetadata fileCenter() {
        return new FileMetadata("f1", 1L, "a.txt", "text/plain", 5,
                FileVisibility.FILE_CENTER, null, null, null, "/my",
                FileBackendType.LOCAL, null, "k1", "active", 7L,
                LocalDateTime.now(), null);
    }

    private FileMetadata businessOnly(String module) {
        return new FileMetadata("f1", 1L, "a.txt", "text/plain", 5,
                FileVisibility.BUSINESS_ONLY, module, "客户", 1L, null,
                FileBackendType.LOCAL, null, "k1", "active", 7L,
                LocalDateTime.now(), null);
    }

    @Test
    void fileCenter_alwaysAllowed_v1() {
        FilePermissionEvaluator eval = new FilePermissionEvaluator(List.of());
        assertThat(eval.canDownload(fileCenter(), 7L)).isTrue();
    }

    @Test
    void businessOnly_noChecker_allowed() {
        FilePermissionEvaluator eval = new FilePermissionEvaluator(List.of());
        assertThat(eval.canDownload(businessOnly("crm"), 7L)).isTrue();
    }

    @Test
    void businessOnly_checkerCalled() {
        AtomicBoolean called = new AtomicBoolean();
        FilePermissionChecker checker = new FilePermissionChecker() {
            @Override public String ownerModule() { return "crm"; }
            @Override public boolean canDownload(FileMetadata file, Long userId) {
                called.set(true);
                return userId != null && userId == 7L;
            }
        };
        FilePermissionEvaluator eval = new FilePermissionEvaluator(List.of(checker));
        assertThat(eval.canDownload(businessOnly("crm"), 7L)).isTrue();
        assertThat(eval.canDownload(businessOnly("crm"), 8L)).isFalse();
        assertThat(called).isTrue();
    }

    @Test
    void businessOnly_unmatchedOwnerModule_allowed() {
        FilePermissionChecker crm = newChecker("crm", false);
        FilePermissionEvaluator eval = new FilePermissionEvaluator(List.of(crm));
        // crm checker 拒绝；但文件 ownerModule=erp 找不到 checker → 默认放行
        assertThat(eval.canDownload(businessOnly("erp"), 7L)).isTrue();
    }

    @Test
    void duplicateCheckerForSameModule_throws() {
        FilePermissionChecker a = newChecker("crm", true);
        FilePermissionChecker b = newChecker("crm", true);
        assertThatThrownBy(() -> new FilePermissionEvaluator(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crm");
    }

    @Test
    void blankOwnerModule_throws() {
        FilePermissionChecker bad = newChecker("  ", true);
        assertThatThrownBy(() -> new FilePermissionEvaluator(List.of(bad)))
                .isInstanceOf(IllegalStateException.class);
    }

    private FilePermissionChecker newChecker(String module, boolean allow) {
        return new FilePermissionChecker() {
            @Override public String ownerModule() { return module; }
            @Override public boolean canDownload(FileMetadata file, Long userId) { return allow; }
        };
    }
}
