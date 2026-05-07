package com.interactive.edu.service.python;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class PythonScriptClient {

    private final PythonClientProperties props;
    private final RestClient restClient;

    public PythonScriptClient(PythonClientProperties props) {
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

    public ScriptPayload generate(PythonScriptRequest request) {
        String url = props.getBaseUrl() + props.getScriptGeneratePath();
        try {
            ScriptEnvelope envelope = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ScriptEnvelope.class);

            if (envelope == null) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "讲稿生成服务返回空响应");
            }
            if (envelope.code() != 0 || envelope.data() == null) {
                throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "讲稿生成服务暂时不可用");
            }

            log.info(
                    "Python script generation succeeded. coursewareId={}, pages={}",
                    request.getCoursewareId(),
                    envelope.data().safePages().size()
            );
            return envelope.data();
        } catch (ServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "讲稿生成服务调用失败", ex);
        }
    }

    public record ScriptEnvelope(int code, String message, ScriptPayload data) {
    }

    public record ScriptPayload(
            @JsonProperty("courseware_id") String coursewareId,
            String opening,
            List<PageScriptPayload> pages,
            String closing
    ) {
        public List<PageScriptPayload> safePages() {
            return pages == null ? Collections.emptyList() : pages;
        }
    }

    public record PageScriptPayload(
            @JsonProperty("page_index") int pageIndex,
            String script,
            String transition
    ) {
    }
}
