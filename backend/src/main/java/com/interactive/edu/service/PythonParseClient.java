package com.interactive.edu.service.python;

import com.interactive.edu.config.PythonClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class PythonParseClient {

    private final PythonClientProperties props;
    private final RestClient restClient;

    public PythonParseClient(PythonClientProperties props) {
        this.props = props;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.getReadTimeout());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public ParsePayload parse(PythonParseRequest req) {
        String url = props.getBaseUrl() + props.getParsePath();
        ParseEnvelope envelope = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ParseEnvelope.class);

        if (envelope == null) {
            throw new IllegalStateException("Python parse service returned empty response");
        }
        if (envelope.code() != 0 || envelope.data() == null) {
            throw new IllegalStateException("Python parse service failed: " + envelope.message());
        }

        log.info("Python parse called ok, coursewareId={}, pages={}",
                req.getCoursewareId(), envelope.data().pages());
        return envelope.data();
    }

    public record ParseEnvelope(int code, String message, ParsePayload data) {
    }

    public record ParsePayload(int pages, List<String> outline, List<ParseSegment> segments) {
        public List<String> safeOutline() {
            return outline == null ? Collections.emptyList() : outline;
        }

        public List<ParseSegment> safeSegments() {
            return segments == null ? Collections.emptyList() : segments;
        }
    }

    public record ParseSegment(int pageIndex, String title, String content, List<String> knowledgePoints) {
        public List<String> safeKnowledgePoints() {
            return knowledgePoints == null ? Collections.emptyList() : knowledgePoints;
        }
    }
}
