package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileExceptionTest {

    @Test
    void carriesCode() {
        FileException ex = new FileException(FileException.Code.SIZE_EXCEEDED, "too big");
        assertThat(ex.code()).isEqualTo(FileException.Code.SIZE_EXCEEDED);
        assertThat(ex.getMessage()).contains("too big");
    }

    @Test
    void preservesCause() {
        Throwable cause = new IllegalArgumentException("inner");
        FileException ex = new FileException(FileException.Code.BACKEND_IO_ERROR, "io", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
