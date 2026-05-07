package com.interactive.edu.client.impl;

import java.nio.ByteBuffer;

public interface DashScopeSpeechSynthesizer extends AutoCloseable {

    ByteBuffer call(String text) throws Exception;

    String getLastRequestId();

    long getFirstPackageDelay();

    @Override
    void close();
}
