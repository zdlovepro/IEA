package com.interactive.edu.client;

import com.interactive.edu.dto.asr.AsrRequest;
import com.interactive.edu.dto.asr.AsrResult;

public interface AsrClient {

    AsrResult recognize(AsrRequest request);
}
