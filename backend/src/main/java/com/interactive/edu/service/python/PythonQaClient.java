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

@Slf4j
@Component
public class PythonQaClient {

    private final PythonClientProperties props;
    private final RestClient restClient;

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

    public record QaEnvelope(int code, String message, PythonQaResponse data) {
    }
}
