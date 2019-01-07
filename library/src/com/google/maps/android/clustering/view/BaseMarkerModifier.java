package com.google.maps.android.clustering.view;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

abstract class BaseMarkerModifier<ADD_TYPE, REMOVE_TYPE> extends Handler implements MessageQueue.IdleHandler {
    private static final int BLANK = 0;

    Queue<ADD_TYPE> mCreateMarkersTasks = new LinkedList<>();
    Queue<ADD_TYPE> mOnScreenCreateMarkersTasks = new LinkedList<>();
    Queue<REMOVE_TYPE> mRemoveMarkersTasks = new LinkedList<>();
    Queue<REMOVE_TYPE> mOnScreenRemoveMarkersTasks = new LinkedList<>();

    final Lock lock = new ReentrantLock();
    private final Condition busyCondition = lock.newCondition();

    /**
     * Whether the idle listener has been added to the UI thread's MessageQueue.
     */
    private boolean mListenerAdded;

    BaseMarkerModifier() {
        super(Looper.getMainLooper());
    }

    @Override
    public final void handleMessage(Message msg) {
        if (!mListenerAdded) {
            Looper.myQueue().addIdleHandler(this);
            mListenerAdded = true;
        }
        removeMessages(BLANK);

        lock.lock();
        try {

            // Perform up to 10 tasks at once.
            // Consider only performing 10 remove tasks, not adds and animations.
            // Removes are relatively slow and are much better when batched.
            for (int i = 0; i < 10; i++) {
                performNextTask();
            }

            if (!isBusy()) {
                mListenerAdded = false;
                Looper.myQueue().removeIdleHandler(this);
                // Signal any other threads that are waiting.
                busyCondition.signalAll();
            } else {
                // Sometimes the idle queue may not be called - schedule up some work regardless
                // of whether the UI thread is busy or not.
                // TODO: try to remove this.
                sendEmptyMessageDelayed(BLANK, 10);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates markers for a cluster some time in the future.
     *
     * @param priority whether this operation should have priority.
     */
    final void add(boolean priority, ADD_TYPE value) {
        lock.lock();
        sendEmptyMessage(BLANK);
        if (priority) {
            mOnScreenCreateMarkersTasks.add(value);
        } else {
            mCreateMarkersTasks.add(value);
        }
        lock.unlock();
    }

    final void remove(boolean priority, REMOVE_TYPE value) {
        lock.lock();
        sendEmptyMessage(BLANK);
        if (priority) {
            mOnScreenRemoveMarkersTasks.add(value);
        } else {
            mRemoveMarkersTasks.add(value);
        }
        lock.unlock();
    }

    /**
     * Perform the next task. Prioritise any on-screen work.
     */
    abstract void performNextTask();

    /**
     * @return true if there is still work to be processed.
     */
    boolean isBusy() {
        try {
            lock.lock();
            return !(mCreateMarkersTasks.isEmpty() && mOnScreenCreateMarkersTasks.isEmpty() &&
                    mOnScreenRemoveMarkersTasks.isEmpty() && mRemoveMarkersTasks.isEmpty()
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks the calling thread until all work has been processed.
     */
    final void waitUntilFree() {
        while (isBusy()) {
            // Sometimes the idle queue may not be called - schedule up some work regardless
            // of whether the UI thread is busy or not.
            // TODO: try to remove this.
            sendEmptyMessage(BLANK);
            lock.lock();
            try {
                if (isBusy()) {
                    busyCondition.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public final boolean queueIdle() {
        // When the UI is not busy, schedule some work.
        sendEmptyMessage(BLANK);
        return true;
    }
}
