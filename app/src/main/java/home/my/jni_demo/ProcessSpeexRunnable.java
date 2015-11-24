package home.my.jni_demo;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Created by legendmohe on 15/11/13.
 */
public class ProcessSpeexRunnable implements Runnable {
    private static final String TAG = "ProcessSpeexRunnable";
    private final WeakReference<ProcessSpeexListener> mListener;

    private Speex mSpeexLib;
    private LinkedBlockingDeque<AudioRawData> mBufferQueue;
    private LinkedList<byte[]> mEncodedData;
    private boolean mStopped = false;

    private static final int POLL_TIMEOUT = 1000;

    private int totByte;

    ProcessSpeexRunnable(LinkedBlockingDeque<AudioRawData> queue, ProcessSpeexListener listener) {
        this.mBufferQueue = queue;
        this.mListener = new WeakReference<ProcessSpeexListener>(listener);
        init();
    }

    @Override
    public void run() {
        if (this.mStopped || mSpeexLib == null) {
            Log.w(TAG, "ProcessSpeexRunnable not runnable.");
            return;
        }
        try {
            while (this.mBufferQueue != null && !this.mStopped) {
                AudioRawData data = null;
                try {
                    data = this.mBufferQueue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "poll error: " + e.toString());
                    continue;
                }
                if (data == null) {
                    continue;
                }
                if (data.len > 0)
                    process(data.data, data.len);
            }
        } finally {
            if (mListener.get() != null) {
                mListener.get().onProcessFinish(mEncodedData);
            }

            this.clean();
            Log.d(TAG, "thread exit.");
        }
    }

    public void stop() {
        this.mStopped = true;
    }

    private void init() {
        mSpeexLib = new Speex();
        mSpeexLib.init();

        mEncodedData = new LinkedList<>();
    }

    private void clean() {
        if (mSpeexLib != null) {
            mSpeexLib.close();
            mSpeexLib = null;
        }
    }

    private void process(short[] buffer, int n) {
        byte[] encoded = new byte[n];
        int encLen = this.mSpeexLib.encode(buffer, 0, encoded, buffer.length);

        totByte += encLen;
        Log.d(TAG, "process speex from " + buffer.length/2 + " byte to " + totByte + " byte");
        if (mEncodedData != null) {
            byte[] wrapData = new byte[encLen];
            System.arraycopy(encoded, 0, wrapData, 0, wrapData.length);
            mEncodedData.add(wrapData);
        }

        if (mListener != null) {
            mListener.get().onProcess(encoded, encLen);
        }
    }

    public interface ProcessSpeexListener {
        void onProcess(byte[] data, int len);
        void onProcessFinish(List<byte[]> data);
    }
}
