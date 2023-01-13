package io.agora.livestreaming.tools;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author create by zhangwei03
 */
public class ThreadTools {
    private static volatile ThreadTools instance;
    private Executor mIOThreadExecutor;
    private Handler mMainThreadHandler;

    private ThreadTools() { init();}

    public static ThreadTools get() {
        if(instance == null) {
            synchronized (ThreadTools.class) {
                if(instance == null) {
                    instance = new ThreadTools();
                }
            }
        }
        return instance;
    }

    private void init() {
        int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        int KEEP_ALIVE_TIME = 1;
        TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingDeque<>();
        mIOThreadExecutor = new ThreadPoolExecutor(NUMBER_OF_CORES,
                NUMBER_OF_CORES * 2,
                                KEEP_ALIVE_TIME,
                                KEEP_ALIVE_TIME_UNIT,
                                taskQueue,
                                new IoThreadFactory(Process.THREAD_PRIORITY_BACKGROUND));
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Switch to an asynchronous thread
     * @param runnable
     */
    public void runOnIOThread(Runnable runnable) {
        mIOThreadExecutor.execute(runnable);
    }

    /**
     * Switch to the UI thread
     * @param runnable
     */
    public void runOnMainThread(Runnable runnable) {
        mMainThreadHandler.post(runnable);
    }

    /**
     * Determine if it is the main thread
     * @return true is main thread
     */
    public boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public void runOnMainThreadDelay(Runnable runnable,long delayMills) {
        mMainThreadHandler.postDelayed(runnable,delayMills);
    }
}
