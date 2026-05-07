package com.interactive.edu.service.courseware;

import com.interactive.edu.dto.CoursewareUploadResult;
import com.interactive.edu.enums.CoursewareStatus;
import com.interactive.edu.enums.TaskStatus;
import com.interactive.edu.exception.BusinessException;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.service.python.PythonParseClient;
import com.interactive.edu.service.python.PythonParseRequest;
import com.interactive.edu.service.python.PythonScriptClient;
import com.interactive.edu.service.python.PythonScriptRequest;
import com.interactive.edu.service.storage.StorageServiceFactory;
import com.interactive.edu.service.storage.StoredObject;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final PythonScriptClient pythonScriptClient;
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
        log.info("Courseware upload accepted. coursewareId={}, filename={}, displayName={}", coursewareId, filename, displayName);

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

    public String triggerScriptGeneration(String coursewareId) {
        ensureCoursewareId(coursewareId);
        CoursewareState state = requireCourseware(coursewareId);

        if (scriptStore.containsKey(coursewareId)) {
            scriptStatusStore.put(coursewareId, CoursewareStatus.READY.name());
            if (!CoursewareStatus.READY.name().equals(state.getStatus())) {
                state.setStatus(CoursewareStatus.READY.name());
                state.touch();
            }
            return CoursewareStatus.READY.name();
        }

        ParsedCourseware parsedCourseware = parsedStore.get(coursewareId);
        if (parsedCourseware == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "课件尚未解析完成，暂时不能生成讲稿");
        }

        String currentStatus = scriptStatusStore.get(coursewareId);
        if (CoursewareStatus.GENERATING_SCRIPT.name().equals(currentStatus)) {
            return currentStatus;
        }

        scriptStatusStore.put(coursewareId, CoursewareStatus.GENERATING_SCRIPT.name());
        state.setStatus(CoursewareStatus.GENERATING_SCRIPT.name());
        state.setCurrentTaskStatus(TaskStatus.RUNNING.name());
        state.touch();
        log.info("Script generation scheduled. coursewareId={}", coursewareId);
        taskExecutor.execute(() -> buildScript(coursewareId, state.getName(), parsedCourseware));
        return CoursewareStatus.GENERATING_SCRIPT.name();
    }

    public ScriptView getScript(String coursewareId) {
        ensureCoursewareId(coursewareId);
        requireCourseware(coursewareId);

        ScriptView script = scriptStore.get(coursewareId);
        if (script != null) {
            return script;
        }

        if (CoursewareStatus.FAILED.name().equals(scriptStatusStore.get(coursewareId))) {
            return new ScriptView(
                    coursewareId,
                    List.of(),
                    List.of(),
                    CoursewareStatus.FAILED.name(),
                    null,
                    null
            );
        }
        return null;
    }

    public String getScriptStatus(String coursewareId) {
        CoursewareState state = requireCourseware(coursewareId);
        return scriptStatusStore.getOrDefault(coursewareId, state.getStatus());
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
                log.warn(
                        "Python parsing unavailable, fallback to local parsing. coursewareId={}, reason={}",
                        state.getId(),
                        ex.getMessage()
                );
            }

            ParsedCourseware parsedCourseware = toParsedCourseware(state, payload);
            parsedStore.put(state.getId(), parsedCourseware);
            state.setStatus(CoursewareStatus.PARSED.name());
            state.setCurrentTaskStatus(TaskStatus.SUCCESS.name());
            state.touch();
            log.info("Courseware parsing completed. coursewareId={}, segments={}", state.getId(), parsedCourseware.segments().size());
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

    private void buildScript(String coursewareId, String coursewareName, ParsedCourseware parsedCourseware) {
        CoursewareState state = requireCourseware(coursewareId);
        try {
            GeneratedScriptDraft draft = buildScriptDraft(coursewareId, coursewareName, parsedCourseware);
            ScriptView baseScriptView = toScriptView(coursewareId, parsedCourseware, draft);
            TtsBatchResult ttsBatchResult = synthesizeSegmentAudioUrls(coursewareId, baseScriptView.segments());
            TaskStatus taskStatus = resolveScriptTaskStatus(ttsBatchResult);
            ScriptView scriptView = new ScriptView(
                    baseScriptView.coursewareId(),
                    baseScriptView.outline(),
                    ttsBatchResult.segments(),
                    baseScriptView.status(),
                    baseScriptView.opening(),
                    baseScriptView.closing()
            );
            scriptStore.put(coursewareId, scriptView);
            markScriptReady(state, taskStatus);
            log.info(
                    "Script generation completed. coursewareId={}, segments={}, ttsSuccessCount={}, ttsFailureCount={}, taskStatus={}",
                    coursewareId,
                    scriptView.segments().size(),
                    ttsBatchResult.successCount(),
                    ttsBatchResult.failureCount(),
                    taskStatus.name()
            );
        } catch (Exception ex) {
            log.error("Script generation failed. coursewareId={}", coursewareId, ex);
            scriptStore.remove(coursewareId);
            scriptStatusStore.put(coursewareId, CoursewareStatus.FAILED.name());
            state.setStatus(CoursewareStatus.FAILED.name());
            state.setCurrentTaskStatus(TaskStatus.FAILED.name());
            state.touch();
        }
    }

    private GeneratedScriptDraft buildScriptDraft(String coursewareId, String coursewareName, ParsedCourseware parsedCourseware) {
        try {
            return buildScriptDraftFromPython(coursewareId, coursewareName, parsedCourseware);
        } catch (Exception ex) {
            log.warn(
                    "Python script generation unavailable, fallback to local template. coursewareId={}, reason={}",
                    coursewareId,
                    ex.getMessage()
            );
            return buildLocalScriptDraft(coursewareName, parsedCourseware);
        }
    }

    private GeneratedScriptDraft buildScriptDraftFromPython(
            String coursewareId,
            String coursewareName,
            ParsedCourseware parsedCourseware
    ) {
        PythonScriptClient.ScriptPayload payload = pythonScriptClient.generate(
                new PythonScriptRequest(
                        coursewareId,
                        coursewareName,
                        null,
                        parsedCourseware.segments().stream()
                                .map(segment -> new PythonScriptRequest.PageContent(
                                        segment.pageIndex(),
                                        segment.title(),
                                        segment.content(),
                                        segment.knowledgePoints()
                                ))
                                .toList(),
                        null
                )
        );

        List<GeneratedPage> generatedPages = payload.safePages().stream()
                .map(page -> new GeneratedPage(page.pageIndex(), page.script(), page.transition()))
                .toList();

        if (generatedPages.isEmpty()) {
            throw new IllegalStateException("Python script generation returned empty pages");
        }
        if (generatedPages.size() != parsedCourseware.segments().size()) {
            log.warn(
                    "Python script page count mismatch. coursewareId={}, parsedPages={}, generatedPages={}",
                    coursewareId,
                    parsedCourseware.segments().size(),
                    generatedPages.size()
            );
        }

        return new GeneratedScriptDraft(payload.opening(), generatedPages, payload.closing());
    }

    private GeneratedScriptDraft buildLocalScriptDraft(String coursewareName, ParsedCourseware parsedCourseware) {
        List<GeneratedPage> pages = new ArrayList<>();
        int totalPages = parsedCourseware.segments().size();
        for (int index = 0; index < totalPages; index++) {
            ParsedSegment parsedSegment = parsedCourseware.segments().get(index);
            pages.add(new GeneratedPage(
                    parsedSegment.pageIndex(),
                    buildFallbackPageScript(parsedSegment, index + 1, totalPages),
                    buildFallbackTransition(index + 1, totalPages, nextTitle(parsedCourseware, index))
            ));
        }

        return new GeneratedScriptDraft(
                "同学们好，接下来我们一起学习《" + coursewareName + "》，我会按页带大家梳理课件中的重点内容。",
                List.copyOf(pages),
                "以上就是这份讲稿的主要内容，建议你结合课件页面再回顾一遍关键知识点。"
        );
    }

    private ScriptView toScriptView(String coursewareId, ParsedCourseware parsedCourseware, GeneratedScriptDraft draft) {
        Map<Integer, GeneratedPage> generatedByPageIndex = new HashMap<>();
        for (GeneratedPage page : draft.pages()) {
            generatedByPageIndex.putIfAbsent(page.pageIndex(), page);
        }

        String opening = defaultText(draft.opening(), "");
        String closing = defaultText(draft.closing(), "");

        List<OutlineItemView> outline = new ArrayList<>();
        List<ScriptSegmentView> segments = new ArrayList<>();
        int totalPages = parsedCourseware.segments().size();

        for (int index = 0; index < totalPages; index++) {
            ParsedSegment parsedSegment = parsedCourseware.segments().get(index);
            GeneratedPage generatedPage = generatedByPageIndex.get(parsedSegment.pageIndex());

            String scriptBody = generatedPage != null && StringUtils.hasText(generatedPage.script())
                    ? generatedPage.script().trim()
                    : buildFallbackPageScript(parsedSegment, index + 1, totalPages);
            String transition = generatedPage != null && StringUtils.hasText(generatedPage.transition())
                    ? generatedPage.transition().trim()
                    : buildFallbackTransition(index + 1, totalPages, nextTitle(parsedCourseware, index));

            String nodeId = "node_" + String.format("%03d", index + 1);
            String content = composeSegmentContent(
                    scriptBody,
                    transition,
                    index == 0 ? opening : null,
                    index == totalPages - 1 ? closing : null
            );

            outline.add(new OutlineItemView(nodeId, parsedSegment.title()));
            segments.add(new ScriptSegmentView(
                    nodeId,
                    nodeId,
                    parsedSegment.pageIndex(),
                    parsedSegment.title(),
                    content,
                    parsedSegment.knowledgePoints(),
                    null
            ));
        }

        return new ScriptView(
                coursewareId,
                List.copyOf(outline),
                List.copyOf(segments),
                CoursewareStatus.READY.name(),
                StringUtils.hasText(opening) ? opening : null,
                StringUtils.hasText(closing) ? closing : null
        );
    }

    private String composeSegmentContent(String scriptBody, String transition, String opening, String closing) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(opening)) {
            parts.add(opening.trim());
        }
        if (StringUtils.hasText(scriptBody)) {
            parts.add(scriptBody.trim());
        }
        if (StringUtils.hasText(transition)) {
            parts.add(transition.trim());
        }
        if (StringUtils.hasText(closing)) {
            parts.add(closing.trim());
        }
        return String.join(" ", parts);
    }

    private TtsBatchResult synthesizeSegmentAudioUrls(String coursewareId, List<ScriptSegmentView> segments) {
        List<ScriptSegmentView> enrichedSegments = new ArrayList<>(segments.size());
        int successCount = 0;
        int failureCount = 0;

        for (ScriptSegmentView segment : segments) {
            String audioUrl = synthesizeAudioUrlSafely(
                    segment.content(),
                    coursewareId,
                    segment.nodeId(),
                    segment.pageIndex()
            );

            if (StringUtils.hasText(audioUrl)) {
                successCount++;
                log.info(
                        "TTS segment generated. coursewareId={}, nodeId={}, pageIndex={}, textLength={}",
                        coursewareId,
                        segment.nodeId(),
                        segment.pageIndex(),
                        segment.content().length()
                );
            } else {
                failureCount++;
                log.info(
                        "TTS segment unavailable. coursewareId={}, nodeId={}, pageIndex={}, textLength={}",
                        coursewareId,
                        segment.nodeId(),
                        segment.pageIndex(),
                        segment.content().length()
                );
            }

            enrichedSegments.add(new ScriptSegmentView(
                    segment.id(),
                    segment.nodeId(),
                    segment.pageIndex(),
                    segment.title(),
                    segment.content(),
                    segment.knowledgePoints(),
                    audioUrl
            ));
        }

        log.info(
                "TTS batch synthesis finished. coursewareId={}, totalSegments={}, successCount={}, failureCount={}",
                coursewareId,
                segments.size(),
                successCount,
                failureCount
        );
        return new TtsBatchResult(List.copyOf(enrichedSegments), successCount, failureCount);
    }

    private String synthesizeAudioUrlSafely(String content, String coursewareId, String nodeId, int pageIndex) {
        try {
            return ttsService.synthesizeToAudioUrl(content);
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "TTS segment rejected due to invalid content. coursewareId={}, nodeId={}, pageIndex={}, textLength={}, reason={}",
                    coursewareId,
                    nodeId,
                    pageIndex,
                    content == null ? 0 : content.length(),
                    ex.getMessage()
            );
            throw ex;
        } catch (Exception ex) {
            log.warn(
                    "TTS generation failed during script build. coursewareId={}, nodeId={}, pageIndex={}, textLength={}, reason={}",
                    coursewareId,
                    nodeId,
                    pageIndex,
                    content == null ? 0 : content.length(),
                    ex.getMessage()
            );
            return null;
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

    private String buildFallbackPageScript(ParsedSegment parsedSegment, int index, int totalPages) {
        StringBuilder builder = new StringBuilder();
        builder.append("现在我们来看第")
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

    private String buildFallbackTransition(int index, int totalPages, String nextTitle) {
        if (index >= totalPages) {
            return "这一页的重点先整理到这里，接下来我们做一个整体回顾。";
        }
        if (StringUtils.hasText(nextTitle)) {
            return "理解了这一页之后，我们继续看看下一页“" + nextTitle + "”，把前后的思路串起来。";
        }
        return "理解了这一页之后，我们继续看下一页，把接下来的重点内容自然串起来。";
    }

    private String nextTitle(ParsedCourseware parsedCourseware, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= parsedCourseware.segments().size()) {
            return null;
        }
        return parsedCourseware.segments().get(nextIndex).title();
    }

    private TaskStatus resolveScriptTaskStatus(TtsBatchResult ttsBatchResult) {
        return ttsBatchResult.failureCount() == 0
                ? TaskStatus.SUCCESS
                : TaskStatus.PARTIAL_SUCCESS;
    }

    private void markScriptReady(CoursewareState state, TaskStatus taskStatus) {
        scriptStatusStore.put(state.getId(), CoursewareStatus.READY.name());
        state.setStatus(CoursewareStatus.READY.name());
        state.setCurrentTaskStatus(taskStatus.name());
        state.touch();
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

    private record GeneratedScriptDraft(String opening, List<GeneratedPage> pages, String closing) {
    }

    private record GeneratedPage(int pageIndex, String script, String transition) {
    }

    private record TtsBatchResult(List<ScriptSegmentView> segments, int successCount, int failureCount) {
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
