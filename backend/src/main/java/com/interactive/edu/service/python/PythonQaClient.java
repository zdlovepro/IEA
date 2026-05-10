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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
public class PythonQaClient {

    private static final Duration STREAM_READ_TIMEOUT = Duration.ofSeconds(60);

    private final PythonClientProperties props;
    private final RestClient restClient;
    private final HttpClient streamHttpClient;

    public PythonQaClient(PythonClientProperties props) {
        this.props = props;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.getReadTimeout());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.streamHttpClient = httpClient;
    }

    public PythonQaResponse askText(PythonQaRequest request) {
        String url = props.getBaseUrl() + props.getQaPath();
        try {
            QaEnvelope envelope = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(QaEnvelope.class);

            if (envelope == null) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "问答服务返回空响应");
            }
            if (envelope.code() != 0 || envelope.data() == null) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "问答服务暂时不可用");
            }

            log.info(
                    "Python QA succeeded. sessionId={}, coursewareId={}, evidenceCount={}, latencyMs={}",
                    request.getSessionId(),
                    request.getCoursewareId(),
                    envelope.data().safeEvidence().size(),
                    envelope.data().latencyMs()
            );
            return envelope.data();
        } catch (ServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "问答服务调用失败", ex);
        }
    }

    public void streamText(PythonQaRequest request, OutputStream outputStream) {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl() + props.getQaStreamPath())
                .queryParam("coursewareId", request.getCoursewareId())
                .queryParam("sessionId", request.getSessionId())
                .queryParam("question", request.getQuestion())
                .queryParam("topK", request.getTopK())
                .queryParamIfPresent("pageIndex", java.util.Optional.ofNullable(request.getPageIndex()))
                .build(true)
                .toUri();

        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .timeout(STREAM_READ_TIMEOUT)
                .build();

        try {
            HttpResponse<java.io.InputStream> response = streamHttpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "问答流式服务暂时不可用");
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "问答流式服务返回格式异常");
            }

            try (java.io.InputStream bodyStream = response.body()) {
                bodyStream.transferTo(outputStream);
                outputStream.flush();
            }

            log.info(
                    "Python QA stream finished. sessionId={}, coursewareId={}, pageIndex={}",
                    request.getSessionId(),
                    request.getCoursewareId(),
                    request.getPageIndex()
            );
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "问答流式服务调用失败", ex);
        }
    }

    public record QaEnvelope(int code, String message, PythonQaResponse data) {
    }
}
