package ui;

import java.util.concurrent.CountDownLatch;

public final class StartupGate {
    private static final CountDownLatch INDEX_DONE = new CountDownLatch(1);
    private static volatile boolean indexStarted = false;
    private static volatile int indexExitCode = Integer.MIN_VALUE;

    private StartupGate() {}

    public static void markIndexStart() {
        indexStarted = true;
    }

    public static void markIndexDone(int exitCode) {
        indexExitCode = exitCode;
        INDEX_DONE.countDown();
    }

    public static boolean isIndexStarted() {
        return indexStarted;
    }

    public static boolean isIndexDone() {
        return INDEX_DONE.getCount() == 0;
    }

    public static int getIndexExitCode() {
        return indexExitCode;
    }

    public static void awaitIndexDone() throws InterruptedException {
        INDEX_DONE.await();
    }
}
