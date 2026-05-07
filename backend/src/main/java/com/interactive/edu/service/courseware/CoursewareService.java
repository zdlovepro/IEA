package com.interactive.edu.service.courseware;

import com.interactive.edu.dto.CoursewareUploadResult;
import com.interactive.edu.service.python.PythonParseClient;
import com.interactive.edu.service.python.PythonParseRequest;
import com.interactive.edu.service.storage.StoredObject;
import com.interactive.edu.service.storage.StorageServiceFactory;
import com.interactive.edu.service.tts.TtsService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoursewareService {

    private final StorageServiceFactory storageServiceFactory;
    private final PythonParseClient pythonParseClient;
    private final TaskExecutor taskExecutor;
    private final TtsService ttsService;

    private final ConcurrentMap<String, CoursewareState> coursewareStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ParsedCourseware> parsedStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScriptView> scriptStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> scriptStatusStore = new ConcurrentHashMap<>();

    public CoursewareUploadResult upload(MultipartFile file, String requestedName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String coursewareId = "cware_" + UUID.randomUUID().toString().replace("-", "");
        String filename = normalizeFilename(file.getOriginalFilename());
        String displayName = resolveDisplayName(requestedName, filename);
        StoredObject storedObject = storageServiceFactory.get().save(coursewareId, file);

        CoursewareState state = new CoursewareState(
                coursewareId,
                displayName,
                filename,
                storedObject.getKey(),
                storedObject.getStorageType(),
                file.getContentType()
        );
        state.setStatus("PARSING");
        state.setCurrentTaskStatus("RUNNING");
        coursewareStore.put(coursewareId, state);

        taskExecutor.execute(() -> completeParse(state));

        return new CoursewareUploadResult(coursewareId, "UPLOADED");
    }

    public CoursewareListView list(int page, int pageSize, String status) {
        if (page <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("page 和 pageSize 必须大于 0");
        }

        List<CoursewareState> filtered = coursewareStore.values().stream()
                .filter(item -> !StringUtils.hasText(status) || status.equalsIgnoreCase(item.getStatus()))
                .sorted(Comparator.comparing(CoursewareState::getCreatedAt).reversed())
                .toList();

        int fromIndex = Math.min((page - 1) * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());

        List<CoursewareListItem> items = filtered.subList(fromIndex, toIndex).stream()
                .map(item -> new CoursewareListItem(
                        item.getId(),
                        item.getName(),
                        item.getStatus(),
                        item.getCreatedAt().toString(),
                        item.getCurrentTaskStatus()
                ))
                .toList();

        return new CoursewareListView(items, filtered.size(), page, pageSize);
    }

    public CoursewareDetailView getDetail(String coursewareId) {
        CoursewareState state = requireCourseware(coursewareId);
        return new CoursewareDetailView(
                state.getId(),
                state.getName(),
                state.getStatus(),
                state.getCurrentTaskStatus(),
                state.getFileType(),
                state.getCreatedAt().toString(),
                state.getUpdatedAt().toString()
        );
    }

    public void triggerScriptGeneration(String coursewareId) {
        ensureCoursewareId(coursewareId);
        requireCourseware(coursewareId);

        if (scriptStore.containsKey(coursewareId)) {
            scriptStatusStore.put(coursewareId, "READY");
            return;
        }

        ParsedCourseware parsedCourseware = parsedStore.get(coursewareId);
        if (parsedCourseware == null) {
            throw new IllegalStateException("课件尚未解析完成，暂时不能生成讲稿");
        }

        String previousStatus = scriptStatusStore.putIfAbsent(coursewareId, "GENERATING");
        if ("GENERATING".equals(previousStatus)) {
            return;
        }

        taskExecutor.execute(() -> buildScript(coursewareId, parsedCourseware));
    }

    public ScriptView getScript(String coursewareId) {
        ensureCoursewareId(coursewareId);
        requireCourseware(coursewareId);
        return scriptStore.get(coursewareId);
    }

    public String getScriptStatus(String coursewareId) {
        requireCourseware(coursewareId);
        return scriptStatusStore.get(coursewareId);
    }

    public ScriptView requireScript(String coursewareId) {
        ScriptView script = getScript(coursewareId);
        if (script == null) {
            throw new IllegalStateException("讲稿尚未生成");
        }
        return script;
    }

    public List<ScriptSegment> getScriptSegments(String coursewareId) {
        return requireScript(coursewareId).segments();
    }

    public ScriptSegment getSegmentForPage(String coursewareId, int pageIndex) {
        List<ScriptSegment> segments = getScriptSegments(coursewareId);
        return segments.stream()
                .filter(segment -> segment.pageIndex() == pageIndex)
                .findFirst()
                .orElse(segments.get(0));
    }

    public CurrentNodeView getCurrentNode(String coursewareId, int pageIndex) {
        ScriptSegment segment = getSegmentForPage(coursewareId, pageIndex);
        return new CurrentNodeView(segment.nodeId(), segment.pageIndex(), segment.content(), segment.audioUrl());
    }

    private void completeParse(CoursewareState state) {
        try {
            PythonParseClient.ParsePayload payload = null;
            try {
                payload = pythonParseClient.parse(new PythonParseRequest(
                        state.getId(),
                        state.getStorageType(),
                        state.getStorageKey(),
                        state.getOriginalFilename(),
                        state.getFileType()
                ));
            } catch (Exception ex) {
                log.warn("Python 解析不可用，回退为本地兜底解析，coursewareId={}, reason={}",
                        state.getId(), ex.getMessage());
            }

            ParsedCourseware parsedCourseware = toParsedCourseware(state, payload);
            parsedStore.put(state.getId(), parsedCourseware);
            state.setStatus("PARSED");
            state.setCurrentTaskStatus("SUCCESS");
            state.touch();
        } catch (Exception ex) {
            log.error("课件解析失败，coursewareId={}", state.getId(), ex);
            state.setStatus("FAILED");
            state.setCurrentTaskStatus("FAILED");
            state.touch();
        }
    }

    private ParsedCourseware toParsedCourseware(CoursewareState state, PythonParseClient.ParsePayload payload) {
        List<ParsedSegment> segments = new ArrayList<>();
        if (payload != null && !payload.safeSegments().isEmpty()) {
            int index = 1;
            for (PythonParseClient.ParseSegment segment : payload.safeSegments()) {
                int pageIndex = segment.pageIndex() > 0 ? segment.pageIndex() : index;
                segments.add(new ParsedSegment(
                        pageIndex,
                        defaultText(segment.title(), "第" + pageIndex + "页"),
                        defaultText(segment.content(), "本页内容正在整理中。"),
                        segment.safeKnowledgePoints()
                ));
                index++;
            }
        }

        if (segments.isEmpty()) {
            segments = buildFallbackSegments(state.getName(), state.getOriginalFilename());
        }

        return new ParsedCourseware(state.getId(), List.copyOf(segments));
    }

    private void buildScript(String coursewareId, ParsedCourseware parsedCourseware) {
        try {
            List<OutlineItem> outline = new ArrayList<>();
            List<ScriptSegment> segments = new ArrayList<>();

            int totalPages = parsedCourseware.segments().size();
            for (int index = 0; index < totalPages; index++) {
                ParsedSegment parsedSegment = parsedCourseware.segments().get(index);
                String nodeId = "node_" + String.format("%03d", index + 1);
                String content = buildScriptContent(parsedSegment, index + 1, totalPages);

                String audioUrl = ttsService.synthesizeToAudioUrl(content);

                ScriptSegment scriptSegment = new ScriptSegment(
                        nodeId,
                        nodeId,
                        parsedSegment.pageIndex(),
                        parsedSegment.title(),
                        content,
                        parsedSegment.knowledgePoints(),
                        audioUrl
                );
                segments.add(scriptSegment);
                outline.add(new OutlineItem(nodeId, parsedSegment.title()));
            }

            scriptStore.put(coursewareId, new ScriptView(coursewareId, List.copyOf(outline), List.copyOf(segments), "READY"));
            scriptStatusStore.put(coursewareId, "READY");

            CoursewareState state = requireCourseware(coursewareId);
            state.setStatus("READY");
            state.touch();
        } catch (Exception ex) {
            log.error("讲稿生成失败，coursewareId={}", coursewareId, ex);
            scriptStatusStore.put(coursewareId, "FAILED");
        }
    }

    private List<ParsedSegment> buildFallbackSegments(String displayName, String originalFilename) {
        String topic = stripExtension(StringUtils.hasText(displayName) ? displayName : originalFilename);
        return List.of(
                new ParsedSegment(
                        1,
                        "课程导入",
                        "这份课件《" + topic + "》将先帮助你建立主题背景，并说明本节课的学习目标。",
                        List.of(topic, "学习目标")
                ),
                new ParsedSegment(
                        2,
                        "核心概念",
                        "中间部分会围绕关键概念、典型例子和应用场景展开，帮助学生建立完整理解。",
                        List.of("核心概念", "案例分析")
                ),
                new ParsedSegment(
                        3,
                        "总结回顾",
                        "最后会回顾重点知识，并提示学生如何把本节内容迁移到后续练习中。",
                        List.of("知识总结", "课后迁移")
                )
        );
    }

    private String buildScriptContent(ParsedSegment parsedSegment, int index, int totalPages) {
        StringBuilder builder = new StringBuilder();
        builder.append("现在我们来看第 ")
                .append(index)
                .append(" / ")
                .append(totalPages)
                .append(" 个讲解节点。");

        if (StringUtils.hasText(parsedSegment.title())) {
            builder.append(" 本页主题是“").append(parsedSegment.title()).append("”。");
        }

        builder.append(" ").append(parsedSegment.content());

        if (!parsedSegment.knowledgePoints().isEmpty()) {
            builder.append(" 你可以重点关注：")
                    .append(String.join("、", parsedSegment.knowledgePoints()))
                    .append("。");
        }

        return builder.toString();
    }

    private CoursewareState requireCourseware(String coursewareId) {
        ensureCoursewareId(coursewareId);
        CoursewareState state = coursewareStore.get(coursewareId);
        if (state == null) {
            throw new NoSuchElementException("课件不存在");
        }
        return state;
    }

    private void ensureCoursewareId(String coursewareId) {
        if (!StringUtils.hasText(coursewareId)) {
            throw new IllegalArgumentException("coursewareId 不能为空");
        }
    }

    private String resolveDisplayName(String requestedName, String filename) {
        if (StringUtils.hasText(requestedName)) {
            return requestedName.trim();
        }
        return stripExtension(filename);
    }

    private String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "courseware.bin";
        }

        String filename = originalFilename.replace('\u0000', ' ').trim().replace('\\', '/');
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }

        return StringUtils.hasText(filename) ? filename : "courseware.bin";
    }

    private String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "未命名课件";
        }

        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    public record CoursewareListView(List<CoursewareListItem> items, int total, int page, int pageSize) {
    }

    public record CoursewareListItem(
            String id,
            String name,
            String status,
            String createdAt,
            String currentTaskStatus
    ) {
    }

    public record CoursewareDetailView(
            String coursewareId,
            String name,
            String status,
            String currentTaskStatus,
            String fileType,
            String createdAt,
            String updatedAt
    ) {
    }

    public record ScriptView(
            String coursewareId,
            List<OutlineItem> outline,
            List<ScriptSegment> segments,
            String status
    ) {
    }

    public record OutlineItem(String id, String title) {
    }

    public record ScriptSegment(
            String id,
            String nodeId,
            int pageIndex,
            String title,
            String content,
            List<String> knowledgePoints,
            String audioUrl
    ) {
    }

    public record CurrentNodeView(String nodeId, int pageIndex, String content, String audioUrl) {
    }

    private record ParsedCourseware(String coursewareId, List<ParsedSegment> segments) {
    }

    private record ParsedSegment(int pageIndex, String title, String content, List<String> knowledgePoints) {
    }

    @Getter
    private static final class CoursewareState {
        private final String id;
        private final String name;
        private final String originalFilename;
        private final String storageKey;
        private final String storageType;
        private final String fileType;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile String status = "UPLOADED";
        private volatile String currentTaskStatus = "PENDING";

        private CoursewareState(
                String id,
                String name,
                String originalFilename,
                String storageKey,
                String storageType,
                String fileType
        ) {
            this.id = id;
            this.name = name;
            this.originalFilename = originalFilename;
            this.storageKey = storageKey;
            this.storageType = storageType;
            this.fileType = StringUtils.hasText(fileType) ? fileType.toUpperCase(Locale.ROOT) : "APPLICATION/OCTET-STREAM";
        }

        private void setStatus(String status) {
            this.status = status;
        }

        private void setCurrentTaskStatus(String currentTaskStatus) {
            this.currentTaskStatus = currentTaskStatus;
        }

        private void touch() {
            this.updatedAt = Instant.now();
        }
    }
}
