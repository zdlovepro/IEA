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
        log.info("Script callback received. coursewareId={}, status={}",
                request.getCoursewareId(), request.getProcessStatus());

        String processStatus = request.getProcessStatus();
        if (!TaskStatus.SUCCESS.name().equalsIgnoreCase(processStatus)
                && !TaskStatus.FAILED.name().equalsIgnoreCase(processStatus)) {
            log.warn("Unknown script callback status ignored. coursewareId={}, processStatus={}",
                    request.getCoursewareId(), processStatus);
            return;
        }

        Optional<Courseware> coursewareOpt = coursewareRepository.findById(request.getCoursewareId());
        Courseware courseware;
        if (coursewareOpt.isEmpty()) {
            log.warn("Courseware missing when callback arrived. Create placeholder. coursewareId={}",
                    request.getCoursewareId());
            courseware = new Courseware();
            courseware.setId(request.getCoursewareId());
            courseware.setStatus(CoursewareStatus.GENERATING_SCRIPT.name());
            courseware = coursewareRepository.save(courseware);
        } else {
            courseware = coursewareOpt.get();
        }

        if (TaskStatus.FAILED.name().equalsIgnoreCase(processStatus)) {
            log.error("Script generation failed from upstream callback. coursewareId={}, reason={}",
                    courseware.getId(), request.getErrorMessage());
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
            for (ScriptCallbackRequest.PageScriptDto page : pages) {
                CoursewarePage cwPage = new CoursewarePage();
                cwPage.setCoursewareId(courseware.getId());
                cwPage.setPageIndex(page.getPageIndex());
                cwPage.setOriginalText(page.getOriginalText());
                coursewarePageRepository.save(cwPage);

                if (page.getScripts() != null) {
                    for (ScriptCallbackRequest.ScriptNodeDto node : page.getScripts()) {
                        LectureScript script = new LectureScript();
                        script.setId(UUID.randomUUID().toString().replace("-", ""));
                        script.setCoursewareId(courseware.getId());
                        script.setPageIndex(page.getPageIndex());
                        script.setNodeId(buildScopedNodeId(courseware.getId(), page.getPageIndex(), node.getNodeId()));
                        script.setContent(node.getContent());
                        script.setAudioUrl(ttsService.synthesizeToAudioUrl(node.getContent()));
                        script.setEditStatus("AUTO");
                        lectureScriptRepository.save(script);
                    }
                }
            }
        }

        courseware.setStatus(CoursewareStatus.READY.name());
        coursewareRepository.save(courseware);
        log.info("Script callback persisted successfully. coursewareId={}, status={}",
                courseware.getId(), CoursewareStatus.READY.name());
    }

    private String buildScopedNodeId(String coursewareId, Integer pageIndex, String nodeId) {
        String scopedNodeId = coursewareId + "_" + pageIndex + "_" + nodeId;
        if (scopedNodeId.length() <= MAX_NODE_ID_LENGTH) {
            return scopedNodeId;
        }

        String digest = UUID.nameUUIDFromBytes(scopedNodeId.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return HASHED_NODE_ID_PREFIX + digest;
    }
}
