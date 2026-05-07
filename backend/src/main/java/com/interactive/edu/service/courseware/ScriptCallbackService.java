package com.interactive.edu.service.courseware;

import com.interactive.edu.dto.callback.ScriptCallbackRequest;
import com.interactive.edu.entity.Courseware;
import com.interactive.edu.entity.CoursewarePage;
import com.interactive.edu.entity.LectureScript;
import com.interactive.edu.enums.CoursewareStatus;
import com.interactive.edu.enums.TaskStatus;
import com.interactive.edu.repository.CoursewarePageRepository;
import com.interactive.edu.repository.CoursewareRepository;
import com.interactive.edu.repository.LectureScriptRepository;
import com.interactive.edu.service.tts.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Profile({"full", "prod"})
@ConditionalOnBean({
        CoursewareRepository.class,
        CoursewarePageRepository.class,
        LectureScriptRepository.class
})
@RequiredArgsConstructor
public class ScriptCallbackService {

    private static final int MAX_NODE_ID_LENGTH = 64;
    private static final String HASHED_NODE_ID_PREFIX = "n_";

    private final CoursewareRepository coursewareRepository;
    private final CoursewarePageRepository coursewarePageRepository;
    private final LectureScriptRepository lectureScriptRepository;
    private final TtsService ttsService;

    @Transactional(rollbackFor = Exception.class)
    public void processScriptCallback(ScriptCallbackRequest request) {
        log.info("Script callback received. coursewareId={}, status={}", request.getCoursewareId(), request.getProcessStatus());

        String processStatus = request.getProcessStatus();
        if (!TaskStatus.SUCCESS.name().equalsIgnoreCase(processStatus)
                && !TaskStatus.FAILED.name().equalsIgnoreCase(processStatus)) {
            log.warn(
                    "Unknown script callback status ignored. coursewareId={}, processStatus={}",
                    request.getCoursewareId(),
                    processStatus
            );
            return;
        }

        Courseware courseware = findOrCreateCourseware(request.getCoursewareId());
        if (isDuplicateSuccessCallback(courseware, processStatus)) {
            log.info("Duplicate script callback ignored. coursewareId={}, status={}", courseware.getId(), processStatus);
            return;
        }

        if (TaskStatus.FAILED.name().equalsIgnoreCase(processStatus)) {
            log.error(
                    "Script generation failed from upstream callback. coursewareId={}, reason={}",
                    courseware.getId(),
                    request.getErrorMessage()
            );
            courseware.setStatus(CoursewareStatus.FAILED.name());
            coursewareRepository.save(courseware);
            return;
        }

        List<ScriptCallbackRequest.PageScriptDto> pages = request.getPages();
        coursewarePageRepository.deleteByCoursewareId(courseware.getId());
        lectureScriptRepository.deleteByCoursewareId(courseware.getId());

        if (pages == null || pages.isEmpty()) {
            log.warn("Callback marked SUCCESS but contains no script pages. coursewareId={}", courseware.getId());
        } else {
            persistScripts(courseware.getId(), pages);
        }

        courseware.setStatus(CoursewareStatus.READY.name());
        coursewareRepository.save(courseware);
        log.info("Script callback persisted successfully. coursewareId={}, status={}", courseware.getId(), CoursewareStatus.READY.name());
    }

    private Courseware findOrCreateCourseware(String coursewareId) {
        Optional<Courseware> coursewareOpt = coursewareRepository.findById(coursewareId);
        if (coursewareOpt.isPresent()) {
            return coursewareOpt.get();
        }

        log.warn("Courseware missing when callback arrived. Create placeholder. coursewareId={}", coursewareId);
        Courseware courseware = new Courseware();
        courseware.setId(coursewareId);
        courseware.setStatus(CoursewareStatus.GENERATING_SCRIPT.name());
        return coursewareRepository.save(courseware);
    }

    private boolean isDuplicateSuccessCallback(Courseware courseware, String processStatus) {
        if (!TaskStatus.SUCCESS.name().equalsIgnoreCase(processStatus)) {
            return false;
        }
        if (!CoursewareStatus.READY.name().equalsIgnoreCase(courseware.getStatus())) {
            return false;
        }
        return !lectureScriptRepository.findByCoursewareIdOrderByPageIndexAsc(courseware.getId()).isEmpty();
    }

    private void persistScripts(String coursewareId, List<ScriptCallbackRequest.PageScriptDto> pages) {
        for (ScriptCallbackRequest.PageScriptDto page : pages) {
            CoursewarePage cwPage = new CoursewarePage();
            cwPage.setCoursewareId(coursewareId);
            cwPage.setPageIndex(page.getPageIndex());
            cwPage.setOriginalText(page.getOriginalText());
            coursewarePageRepository.save(cwPage);

            if (page.getScripts() == null) {
                continue;
            }

            for (ScriptCallbackRequest.ScriptNodeDto node : page.getScripts()) {
                LectureScript script = new LectureScript();
                script.setId(UUID.randomUUID().toString().replace("-", ""));
                script.setCoursewareId(coursewareId);
                script.setPageIndex(page.getPageIndex());
                script.setNodeId(buildScopedNodeId(coursewareId, page.getPageIndex(), node.getNodeId()));
                script.setContent(node.getContent());
                script.setAudioUrl(synthesizeAudioUrlSafely(coursewareId, page.getPageIndex(), node.getContent()));
                script.setEditStatus("AUTO");
                lectureScriptRepository.save(script);
            }
        }
    }

    private String synthesizeAudioUrlSafely(String coursewareId, Integer pageIndex, String content) {
        try {
            return ttsService.synthesizeToAudioUrl(content);
        } catch (Exception ex) {
            log.warn(
                    "TTS generation failed during callback persistence. coursewareId={}, pageIndex={}, reason={}",
                    coursewareId,
                    pageIndex,
                    ex.getMessage()
            );
            return null;
        }
    }

    private String buildScopedNodeId(String coursewareId, Integer pageIndex, String nodeId) {
        String safeNodeId = StringUtils.hasText(nodeId) ? nodeId : UUID.randomUUID().toString().replace("-", "");
        String scopedNodeId = coursewareId + "_" + pageIndex + "_" + safeNodeId;
        if (scopedNodeId.length() <= MAX_NODE_ID_LENGTH) {
            return scopedNodeId;
        }

        String digest = UUID.nameUUIDFromBytes(scopedNodeId.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return HASHED_NODE_ID_PREFIX + digest;
    }
}
