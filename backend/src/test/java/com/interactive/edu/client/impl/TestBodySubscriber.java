package com.interactive.edu.client.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

final class TestBodySubscriber implements Flow.Subscriber<ByteBuffer> {

    private final List<ByteBuffer> buffers = new ArrayList<>();
    private final CompletableFuture<String> bodyFuture = new CompletableFuture<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(ByteBuffer item) {
        ByteBuffer copy = ByteBuffer.allocate(item.remaining());
        copy.put(item.duplicate());
        copy.flip();
        buffers.add(copy);
    }

    @Override
    public void onError(Throwable throwable) {
        bodyFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        int totalLength = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
        byte[] bytes = new byte[totalLength];
        int offset = 0;
        for (ByteBuffer buffer : buffers) {
            int length = buffer.remaining();
            buffer.get(bytes, offset, length);
            offset += length;
        }
        bodyFuture.complete(new String(bytes, StandardCharsets.UTF_8));
    }

    String awaitBody() {
        return bodyFuture.join();
    }
}
