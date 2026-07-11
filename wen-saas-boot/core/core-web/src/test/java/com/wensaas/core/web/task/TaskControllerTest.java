package com.wensaas.core.web.task;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskPage;
import com.wensaas.core.api.task.TaskQuery;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskSnapshot;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.web.common.ValidationRestExceptionAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TaskControllerTest {

    private TaskService taskService;
    private TaskStreamService streamService;
    private FileService fileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        taskService = mock(TaskService.class);
        streamService = mock(TaskStreamService.class);
        fileService = mock(FileService.class);
        TaskController controller = new TaskController(taskService, streamService, fileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TaskRestExceptionAdvice(), new ValidationRestExceptionAdvice())
                .build();
        TenantContext.set(9L);
        UserContext.set(new UserPrincipal(7L, 9L, "alice", "s", false, 11L, false, null));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        UserContext.clear();
    }

    private TaskSnapshot snapshot(long id, TaskStatus status, Map<String, Object> result) {
        return new TaskSnapshot(id, 9L, TaskType.IMPORT, "导入成员-x.xlsx", "sys.task.import.member",
                status, 40, 100, 40, 38, 2, result, null, 7L,
                LocalDateTime.now(), LocalDateTime.now(), null, LocalDateTime.now().plusDays(30));
    }

    @Test
    void list_happyPath_returnsPage() throws Exception {
        when(taskService.queryMine(eq(7L), any(TaskQuery.class)))
                .thenReturn(new TaskPage(List.of(snapshot(5L, TaskStatus.RUNNING, null)), 1));

        mockMvc.perform(get("/api/v1/tasks").param("page", "1").param("size", "20"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString())
                        .contains("导入成员-x.xlsx").contains("running").contains("import"));
    }

    @Test
    void list_sizeOverPageQueryMax_returns422() throws Exception {
        // PageQuery 规范：size 上限 200，越界 422（不再静默钳制）
        mockMvc.perform(get("/api/v1/tasks").param("page", "1").param("size", "300"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(422));
        verify(taskService, never()).queryMine(anyLong(), any());
    }

    @Test
    void list_invalidStatusFilter_returns422() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "bogus"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(422));
        verify(taskService, never()).queryMine(anyLong(), any());
    }

    @Test
    void get_happyPath() throws Exception {
        when(taskService.get(5L)).thenReturn(snapshot(5L, TaskStatus.COMPLETED, null));
        mockMvc.perform(get("/api/v1/tasks/5"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("completed"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(taskService.get(99L))
                .thenThrow(new TaskException(TaskException.Code.TASK_NOT_FOUND, "任务不存在"));
        mockMvc.perform(get("/api/v1/tasks/99"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void cancel_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/cancel"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(204));
        verify(taskService).cancel(5L);
    }

    @Test
    void cancel_notRunning_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new TaskException(TaskException.Code.CANCEL_NOT_ALLOWED, "非运行态"))
                .when(taskService).cancel(5L);
        mockMvc.perform(post("/api/v1/tasks/5/cancel"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(409));
    }

    @Test
    void download_redirectsToSignedUrl() throws Exception {
        when(taskService.get(5L)).thenReturn(snapshot(5L, TaskStatus.COMPLETED, Map.of("fileId", "f1")));
        when(fileService.signedDownloadUrl(eq("f1"), any(Duration.class)))
                .thenReturn("https://oss/get?sig=abc");

        mockMvc.perform(get("/api/v1/tasks/5/download"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(302))
                .andExpect(r -> assertThat(r.getResponse().getHeader("Location")).contains("oss/get?sig=abc"));
    }

    @Test
    void download_noResultFile_returns404() throws Exception {
        when(taskService.get(5L)).thenReturn(snapshot(5L, TaskStatus.COMPLETED, null));
        mockMvc.perform(get("/api/v1/tasks/5/download"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
        verify(fileService, never()).signedDownloadUrl(anyString(), any());
    }

    @Test
    void stream_taskMissing_returns404_withoutOpening() throws Exception {
        when(taskService.get(99L))
                .thenThrow(new TaskException(TaskException.Code.TASK_NOT_FOUND, "任务不存在"));
        mockMvc.perform(get("/api/v1/tasks/99/stream"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
        verify(streamService, never()).open(anyLong(), anyLong());
    }

    @Test
    void stream_happyPath_opensStream() throws Exception {
        when(taskService.get(5L)).thenReturn(snapshot(5L, TaskStatus.RUNNING, null));
        when(streamService.open(eq(5L), eq(9L))).thenReturn(new SseEmitter());
        mockMvc.perform(get("/api/v1/tasks/5/stream"));
        verify(streamService).open(5L, 9L);
    }
}
