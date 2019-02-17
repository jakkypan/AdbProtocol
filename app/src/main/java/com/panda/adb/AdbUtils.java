package com.panda.adb;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.panda.adb.cgutman.AdbBase64;
import com.panda.adb.cgutman.AdbConnection;
import com.panda.adb.cgutman.AdbCrypto;
import com.panda.adb.cgutman.AdbStream;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * adb命令执行的工具类
 */
public class AdbUtils {
    private static AdbConnection connection;
    private static final Executor THREAD_POOL_EXECUTOR;
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;

    static {
        ThreadFactory sThreadFactory = new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                return new Thread(r, "ADB_TASK#" + mCount.getAndIncrement());
            }
        };
        int CPU_COUNT = Runtime.getRuntime().availableProcessors();
        int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
        int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(128), sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    /**
     * 执行adb shell的命令
     *
     * @param context
     * @param cmd
     * @param callback
     */
    public static void executeShell(final Context context, final String cmd, final AdbCallback callback) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null) {
                        connection = connection(context);
                    }
                    AdbStream stream = connection.open(cmd);
                    StringBuilder stringBuilder = new StringBuilder();
                    byte[] bytes = stream.read();
                    stringBuilder.append(new String(bytes));
                    while (bytes != null) {
                        stringBuilder.append(new String(bytes));
                        try {
                            bytes = stream.read();
                        } catch (Exception e) {
                            bytes = null;
                        }
                    }
                    String response = stringBuilder.toString();
                    if (!TextUtils.isEmpty(response)) {
                        if (callback != null) {
                            callback.onSuccess(response);
                        }
                    } else {
                        if (callback != null) {
                            callback.onFail("");
                        }
                    }
                } catch (Throwable e) {
                    if (callback != null) {
                        callback.onFail(e.getMessage());
                    }
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(runnable);
    }

    /**
     * 建立和adb之间的连接，这样才能执行上面的{@link AdbUtils#executeShell(Context, String, AdbCallback)}命令
     *
     * @return
     */
    private static AdbConnection connection(Context context) {
        // step1：load or generate RSA keys, devices running Android 4.2.2 and later use RSA keys to authenticate the ADB connection.
        String path = context.getCacheDir().getAbsolutePath();
        File pub = new File(path + File.separatorChar + "pub.key");
        File priv = new File(path + File.separatorChar + "priv.key");
        AdbCrypto crypto = null;
        if (pub.exists() && priv.exists()) {
            try {
                crypto = AdbCrypto.loadAdbKeyPair(getBase64Impl(), priv, pub);
            } catch (Exception ignored) {}
        }
        if (crypto == null) {
            try {
                crypto = AdbCrypto.generateAdbKeyPair(getBase64Impl());
                crypto.saveAdbKeyPair(priv, pub);
            } catch (NoSuchAlgorithmException | IOException e) {
                e.printStackTrace();
            }
        }
        if (crypto == null) return null;

        // step 2：build the socket channel
        Socket sock = null;
        try {
            sock = new Socket(HOST, PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sock == null) return null;

        // step 3：connect to the adb
        AdbConnection adb = null;
        try {
            adb = AdbConnection.create(sock, crypto);
            adb.connect();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return adb;
    }

    private static AdbBase64 getBase64Impl() {
        return new AdbBase64() {
            @Override
            public String encodeToString(byte[] data) {
                return Base64.encodeToString(data, Base64.DEFAULT);
            }
        };
    }

    public interface AdbCallback {
        void onSuccess(String adbResponse);
        void onFail(String failString);
    }
}
