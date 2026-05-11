package com.interactive.edu.service.python;

import com.interactive.edu.config.PythonClientProperties;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        long startAt = System.currentTimeMillis();
        try {
            ParseEnvelope envelope = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(ParseEnvelope.class);

            if (envelope == null) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "课件解析服务返回空响应");
            }
            if (envelope.code() != 0 || envelope.data() == null) {
                throw new ServiceException(
                        ErrorCode.PYTHON_SERVICE_ERROR,
                        "课件解析服务暂时不可用"
                );
            }

            log.info(
                    "Python parse succeeded. coursewareId={}, pages={}, latencyMs={}",
                    req.getCoursewareId(),
                    envelope.data().pages(),
                    Math.max(1, System.currentTimeMillis() - startAt)
            );
            return envelope.data();
        } catch (ServiceException ex) {
            log.warn(
                    "Python parse returned business failure. coursewareId={}, latencyMs={}, reason={}",
                    req.getCoursewareId(),
                    Math.max(1, System.currentTimeMillis() - startAt),
                    ex.getMessage()
            );
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "课件解析服务调用失败", ex);
        }
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
