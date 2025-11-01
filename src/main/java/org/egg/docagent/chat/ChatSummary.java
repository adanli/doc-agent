package org.egg.docagent.chat;

import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

public class ChatSummary implements Runnable {
    private final AtomicInteger successCount;
    private final AtomicInteger errorCount;
    private final AtomicInteger skipCount;
    private final int total;

    @Setter
    private boolean flag;

    public ChatSummary(AtomicInteger successCount, AtomicInteger errorCount, AtomicInteger skipCount, int total, boolean flag) {
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.skipCount = skipCount;
        this.total = total;
        this.flag = flag;
    }

    @Override
    public void run() {
        while (flag) {
            try {
                Thread.sleep(10000);
                System.err.printf("成功进度: %d/%d, 失败进度: %d/%d, 跳过进度: %d/%d%n", successCount.get(), total, errorCount.get(), total, skipCount.get(), total);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
