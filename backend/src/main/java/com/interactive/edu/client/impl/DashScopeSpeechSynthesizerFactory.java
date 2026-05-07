package com.interactive.edu.client.impl;

public interface DashScopeSpeechSynthesizerFactory {

    DashScopeSpeechSynthesizer create(DashScopeTtsOptions options) throws Exception;
}
