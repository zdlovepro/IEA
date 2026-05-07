package com.interactive.edu.client;

import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;

import java.util.concurrent.CompletableFuture;

/**
 * TTS 文本转语音内部接口。
 * <p>
 * 提供同步与异步两种语音合成方式，供 Service 层按需调用。
 * 当前仅有阿里云 NLS 实现 {@code AliyunTtsClient}；
 * 后续如需切换厂商，只需增加新实现并通过配置指定即可。
 */
public interface TtsClient {

    /**
     * 同步文本转语音。
     * <p>
     * 调用方线程阻塞等待，直到音频数据完整返回。
     * 适用于数据量小、要求即时获得结果的场景（如单段脚本实时播报）。
     *
     * @param request TTS 合成请求
     * @return 合成结果，包含音频字节数组及元信息
     * @throws RuntimeException 当 TTS 服务不可用或鉴权失败时抛出
     */
    TtsResult synthesize(TtsRequest request);

    /**
     * 异步文本转语音。
     * <p>
     * 立即返回 {@link CompletableFuture}，合成任务在独立线程池（ttsTaskExecutor）中执行。
     * 适用于批量合成（如课件全页 TTS 预生成）等不阻塞主流程的场景。
     *
     * @param request TTS 合成请求
     * @return 包含合成结果的 Future
     */
    CompletableFuture<TtsResult> synthesizeAsync(TtsRequest request);
}
