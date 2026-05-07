package com.interactive.edu.service.courseware;

import com.interactive.edu.dto.CoursewareUploadResult;
import com.interactive.edu.enums.CoursewareStatus;
import com.interactive.edu.enums.TaskStatus;
import com.interactive.edu.exception.BusinessException;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.service.python.PythonParseClient;
import com.interactive.edu.service.python.PythonParseRequest;
import com.interactive.edu.service.storage.StoredObject;
import com.interactive.edu.service.storage.StorageServiceFactory;
import com.interactive.edu.service.tts.TtsService;
import com.interactive.edu.vo.courseware.CoursewareDetailView;
import com.interactive.edu.vo.courseware.CoursewareListItem;
import com.interactive.edu.vo.courseware.CoursewareListView;
import com.interactive.edu.vo.courseware.CurrentNodeView;
import com.interactive.edu.vo.courseware.OutlineItemView;
import com.interactive.edu.vo.courseware.ScriptSegmentView;
import com.interactive.edu.vo.courseware.ScriptView;
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

    private static final String SCRIPT_STATUS_GENERATING = "GENERATING";
    private static final String SCRIPT_STATUS_READY = "READY";
    private static final String SCRIPT_STATUS_FAILED = "FAILED";

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
        log.info("Courseware upload accepted. coursewareId={}, filename={}, displayName={}",
                coursewareId, filename, displayName);

        StoredObject storedObject = storageServiceFactory.get().save(coursewareId, file);
        CoursewareState state = new CoursewareState(
                coursewareId,
                displayName,
                filename,
                storedObject.getKey(),
                storedObject.getStorageType(),
                file.getContentType()
        );
        state.setStatus(CoursewareStatus.PARSING.name());
        state.setCurrentTaskStatus(TaskStatus.RUNNING.name());
        coursewareStore.put(coursewareId, state);

        taskExecutor.execute(() -> completeParse(state));
        return new CoursewareUploadResult(coursewareId, CoursewareStatus.UPLOADED.name());
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
        CoursewareState state = requireCourseware(coursewareId);

        if (scriptStore.containsKey(coursewareId)) {
            scriptStatusStore.put(coursewareId, SCRIPT_STATUS_READY);
            state.setStatus(CoursewareStatus.READY.name());
            state.setCurrentTaskStatus(TaskStatus.SUCCESS.name());
            state.touch();
            return;
        }

        ParsedCourseware parsedCourseware = parsedStore.get(coursewareId);
        if (parsedCourseware == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "课件尚未解析完成，暂时不能生成讲稿");
        }

        String previousStatus = scriptStatusStore.putIfAbsent(coursewareId, SCRIPT_STATUS_GENERATING);
        if (SCRIPT_STATUS_GENERATING.equals(previousStatus)) {
            return;
        }

        state.setStatus(CoursewareStatus.GENERATING_SCRIPT.name());
        state.setCurrentTaskStatus(TaskStatus.RUNNING.name());
        state.touch();
        log.info("Script generation started. coursewareId={}", coursewareId);
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
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "讲稿尚未生成");
        }
        return script;
    }

    public List<ScriptSegmentView> getScriptSegments(String coursewareId) {
        return requireScript(coursewareId).segments();
    }

    public ScriptSegmentView getSegmentForPage(String coursewareId, int pageIndex) {
        List<ScriptSegmentView> segments = getScriptSegments(coursewareId);
        return segments.stream()
                .filter(segment -> segment.pageIndex() == pageIndex)
                .findFirst()
                .orElse(segments.get(0));
    }

    public CurrentNodeView getCurrentNode(String coursewareId, int pageIndex) {
        ScriptSegmentView segment = getSegmentForPage(coursewareId, pageIndex);
        return new CurrentNodeView(segment.nodeId(), segment.pageIndex(), segment.content(), segment.audioUrl());
    }

    private void completeParse(CoursewareState state) {
        try {
            log.info("Courseware parsing started. coursewareId={}", state.getId());
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
                log.warn("Python parsing unavailable, fallback to local parsing. coursewareId={}, reason={}",
                        state.getId(), ex.getMessage());
            }

            ParsedCourseware parsedCourseware = toParsedCourseware(state, payload);
            parsedStore.put(state.getId(), parsedCourseware);
            state.setStatus(CoursewareStatus.PARSED.name());
            state.setCurrentTaskStatus(TaskStatus.SUCCESS.name());
            state.touch();
            log.info("Courseware parsing completed. coursewareId={}, segments={}",
                    state.getId(), parsedCourseware.segments().size());
        } catch (Exception ex) {
            log.error("Courseware parsing failed. coursewareId={}", state.getId(), ex);
            state.setStatus(CoursewareStatus.FAILED.name());
            state.setCurrentTaskStatus(TaskStatus.FAILED.name());
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
                        defaultText(segment.title(), "第 " + pageIndex + " 页"),
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
            List<OutlineItemView> outline = new ArrayList<>();
            List<ScriptSegmentView> segments = new ArrayList<>();

            int totalPages = parsedCourseware.segments().size();
            for (int index = 0; index < totalPages; index++) {
                ParsedSegment parsedSegment = parsedCourseware.segments().get(index);
                String nodeId = "node_" + String.format("%03d", index + 1);
                String content = buildScriptContent(parsedSegment, index + 1, totalPages);
                String audioUrl = ttsService.synthesizeToAudioUrl(content);

                segments.add(new ScriptSegmentView(
                        nodeId,
                        nodeId,
                        parsedSegment.pageIndex(),
                        parsedSegment.title(),
                        content,
                        parsedSegment.knowledgePoints(),
                        audioUrl
                ));
                outline.add(new OutlineItemView(nodeId, parsedSegment.title()));
            }

            scriptStore.put(coursewareId, new ScriptView(
                    coursewareId,
                    List.copyOf(outline),
                    List.copyOf(segments),
                    SCRIPT_STATUS_READY
            ));
            scriptStatusStore.put(coursewareId, SCRIPT_STATUS_READY);

            CoursewareState state = requireCourseware(coursewareId);
            state.setStatus(CoursewareStatus.READY.name());
            state.setCurrentTaskStatus(TaskStatus.SUCCESS.name());
            state.touch();
            log.info("Script generation completed. coursewareId={}, segments={}", coursewareId, segments.size());
        } catch (Exception ex) {
            log.error("Script generation failed. coursewareId={}", coursewareId, ex);
            scriptStatusStore.put(coursewareId, SCRIPT_STATUS_FAILED);
            CoursewareState state = requireCourseware(coursewareId);
            state.setStatus(CoursewareStatus.FAILED.name());
            state.setCurrentTaskStatus(TaskStatus.FAILED.name());
            state.touch();
        }
    }

    private List<ParsedSegment> buildFallbackSegments(String displayName, String originalFilename) {
        String topic = stripExtension(StringUtils.hasText(displayName) ? displayName : originalFilename);
        return List.of(
                new ParsedSegment(
                        1,
                        "课程导入",
                        "这份课件《" + topic + "》会先帮助学生建立主题背景，并说明本节课的学习目标。",
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
        private volatile String status = CoursewareStatus.UPLOADED.name();
        private volatile String currentTaskStatus = TaskStatus.PENDING.name();

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
