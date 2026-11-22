package com.fashionapp.domain.clothes;

import java.util.concurrent.Executor;

/** Runs startup backfills (FR-08) on their own daemon thread so they never delay or fail application startup. */
final class BackgroundStarter {

    private BackgroundStarter() {
    }

    static Executor daemon(String threadName) {
        return task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            thread.start();
        };
    }
}
