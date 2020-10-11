package com.example.downloadapkandinstall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UpdateActivity extends AppCompatActivity {
    private static final String TAG = UpdateActivity.class.getSimpleName();
    private static final int PROGRESS = 100;
    private Button button1;
    private static String URL_STRING = "";//下载文件的地址
    private static int down = 0;//状态码
    File file;
    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case 1:
                    button1.setText("点击安装");
                    down = 1;
                    break;
                case 2:
                    down = 2;
                    button1.setText("打开");
                    break;
                case PROGRESS:
                    String result = (String) msg.obj;
                    mTvProgress.setText(result);
                    break;
            }
        }

    };
    private TextView mTvProgress;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        initParamsAndValues();
        initView();

        String apk = getIntent().getStringExtra("apk");
        String path = "http://59.110.12.225:8080/szcb.admin";
        URL_STRING = path + apk;
        URL_STRING = "https://hdl-emas-app-bucket.oss-cn-beijing.aliyuncs.com/app/ipa/apk/chendexiang/app-debug.apk";
        //调用手机中的浏览器下载
//        Uri uri = Uri.parse(path+apk);
//        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//        startActivity(intent);

        button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // 下载apk
                if (down == 0) {
                    downFile(URL_STRING);
                    button1.setText("正在下载");
                    // 安装APK
                } else if (down == 1) {
                    installApk();
                    // 打开apk
                } else if (down == 2) {
                    openApk(UpdateActivity.this, URL_STRING);
                }

            }
        });

    }

    private void initParamsAndValues() {
        mContext = this;
    }

    private void initView() {
        mTvProgress = findViewById(R.id.tv_progress);
    }

    // 接收到安装完成apk的广播
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            System.out.println("接收到安装完成apk的广播");

            Message message = handler.obtainMessage();
            message.what = 2;
            handler.sendMessage(message);
        }
    };

    /**
     * 后台在下面一个Apk 下载完成后返回下载好的文件
     *
     * @param httpUrl
     * @return
     */
    private File downFile(final String httpUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(httpUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    FileOutputStream fileOutputStream = null;
                    InputStream inputStream;
                    if (connection.getResponseCode() == 200) {
                        inputStream = connection.getInputStream();
                        if (inputStream != null) {
                            final int appLength = connection.getContentLength();
                            file = getFile(httpUrl);
                            fileOutputStream = new FileOutputStream(file);
                            byte[] buffer = new byte[1024];
                            int length = 0;
                            int total = 0;
                            while ((length = inputStream.read(buffer)) != -1) {
                                total += length;
                                String result = total * 1.0 / appLength * 100 + "%";
                                fileOutputStream.write(buffer, 0, length);
                                Message message = handler.obtainMessage();
                                message.what = PROGRESS;
                                message.obj = result;
                                handler.sendMessage(message);
                            }
                            fileOutputStream.close();
                            fileOutputStream.flush();
                        }
                        inputStream.close();
                    }
                    // 往handler发送一条消息 更改button的text属性
                    Message message = handler.obtainMessage();
                    message.what = 1;
                    handler.sendMessage(message);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return file;
    }

    /**
     * 安装APK
     */
    private void installApk() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (file != null && file.exists()){
            // 兼容7.0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri contentUri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".fileProvider", file);
                intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                //兼容8.0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    boolean hasInstallPermission = mContext.getPackageManager().canRequestPackageInstalls();
                    if (!hasInstallPermission) {
                        startInstallPermissionSettingActivity();
                        return;
                    }
                }
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if (mContext.getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                mContext.startActivity(intent);
            }
        }
    }

    private void startInstallPermissionSettingActivity() {
        //注意这个是8.0新API
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        // 注册一个广播
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 解除广播
        unregisterReceiver(broadcastReceiver);
    }

    /**
     * 打开已经安装好的apk
     */
    private void openApk(Context context, String url) {
        PackageManager manager = context.getPackageManager();
        // 这里的是你下载好的文件路径
        PackageInfo info = manager.getPackageArchiveInfo(Environment.getExternalStorageDirectory().getAbsolutePath() + getFilePath(url), PackageManager.GET_ACTIVITIES);
        if (info != null) {
            Intent intent = manager.getLaunchIntentForPackage(info.applicationInfo.packageName);
            startActivity(intent);
        }
    }

    /**
     * 根据传过来url创建文件
     */
    private File getFile(String url) {
//        File files = new File(Environment.getExternalStorageDirectory().getAbsoluteFile(), getFilePath(url));
//        return files;
        File files = new File(getExternalCacheDir(),getFilePath(url));
        return files;
    }

    /**
     * 截取出url后面的apk的文件名    
     * @param url    
     * @return
     */
    private String getFilePath(String url) {
        return url.substring(url.lastIndexOf("/"), url.length());
    }
}