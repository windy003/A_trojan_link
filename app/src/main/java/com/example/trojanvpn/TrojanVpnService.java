package com.example.trojanvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrojanVpnService extends VpnService {
    private static final String TAG = "TrojanVpnService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "TrojanVPN_Channel";
    
    public static final String ACTION_CONNECT = "com.example.trojanvpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.example.trojanvpn.DISCONNECT";
    public static final String EXTRA_TROJAN_LINK = "trojan_link";
    
    private ExecutorService executorService;
    private ParcelFileDescriptor vpnInterface = null;
    private boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newFixedThreadPool(2);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONNECT.equals(intent.getAction())) {
            String trojanLink = intent.getStringExtra(EXTRA_TROJAN_LINK);
            if (trojanLink != null) {
                startForeground(NOTIFICATION_ID, createNotification("Trojan VPN 正在运行"));
                connectVpn(trojanLink);
            }
        } else if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnectVpn();
            stopForeground(true);
            stopSelf();
        }
        
        return START_STICKY;
    }
    
    private void connectVpn(String trojanLink) {
        if (isRunning) {
            Log.i(TAG, "VPN 已经在运行");
            return;
        }
        
        // 解析Trojan链接
        TrojanLinkParser parser = new TrojanLinkParser();
        if (!parser.parse(trojanLink)) {
            Log.e(TAG, "解析Trojan链接失败");
            return;
        }
        
        // 获取SNI和allowInsecure参数
        String sni = parser.getSni();
        boolean allowInsecure = parser.getAllowInsecure();
        
        // 配置VPN
        try {
            Log.i(TAG, "正在配置VPN...");
            
            // 构建VPN接口
            Builder builder = new Builder()
                    .setSession("TrojanVPN")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("8.8.4.4")
                    .setMtu(1500);
            
            // 允许绕过VPN的应用
            // builder.addDisallowedApplication(getPackageName());
            
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "创建VPN接口失败");
                return;
            }
            
            isRunning = true;
            updateNotification("Trojan VPN 已连接");
            
            // 启动VPN处理线程
            executorService.submit(new VpnRunnable(
                    vpnInterface, 
                    parser.getServer(),
                    parser.getPort(),
                    parser.getPassword(),
                    sni,
                    allowInsecure));
            
            Log.i(TAG, "VPN 服务已启动");
            
        } catch (Exception e) {
            Log.e(TAG, "启动VPN失败: " + e.getMessage(), e);
            disconnectVpn();
        }
    }
    
    private void disconnectVpn() {
        Log.i(TAG, "正在断开VPN连接...");
        isRunning = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
            } catch (Exception e) {
                Log.e(TAG, "关闭VPN接口失败: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "销毁VPN服务");
        disconnectVpn();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }
    
    // 创建通知渠道
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Trojan VPN 服务",
                    NotificationManager.IMPORTANCE_LOW);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    // 创建通知
    private Notification createNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Trojan VPN")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }
    
    // 更新通知
    private void updateNotification(String content) {
        NotificationManager manager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }
    
    // VPN处理线程
    private class VpnRunnable implements Runnable {
        private final ParcelFileDescriptor vpnInterface;
        private final String server;
        private final int port;
        private final String password;
        private final String sni;
        private final boolean allowInsecure;
        private TrojanConnection trojanConnection;
        
        public VpnRunnable(ParcelFileDescriptor vpnInterface, String server, 
                           int port, String password, String sni, boolean allowInsecure) {
            this.vpnInterface = vpnInterface;
            this.server = server;
            this.port = port;
            this.password = password;
            this.sni = sni;
            this.allowInsecure = allowInsecure;
        }
        
        @Override
        public void run() {
            FileInputStream vpnInput = null;
            FileOutputStream vpnOutput = null;
            
            try {
                Log.i(TAG, "VPN线程启动中...");
                vpnInput = new FileInputStream(vpnInterface.getFileDescriptor());
                vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor());
                
                // 连接到Trojan服务器
                trojanConnection = new TrojanConnection(server, port, password, sni, allowInsecure);
                
                // 等待连接建立
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "线程中断");
                    return;
                }
                
                ByteBuffer packet = ByteBuffer.allocate(32767);
                
                // 主VPN处理循环
                while (isRunning) {
                    // 从VPN接口读取数据
                    int length = vpnInput.read(packet.array());
                    if (length > 0) {
                        // 处理VPN数据包
                        packet.limit(length);
                        
                        // 将流量转发到Trojan服务器
                        byte[] processedData = trojanConnection.processOutgoing(packet.array(), length);
                        
                        // 处理返回的数据
                        if (processedData != null && processedData.length > 0) {
                            vpnOutput.write(processedData);
                            vpnOutput.flush();
                        }
                        
                        packet.clear();
                    }
                    
                    // 添加小延迟，避免CPU占用过高
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "线程中断");
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "VPN处理错误: " + e.getMessage(), e);
                updateNotification("Trojan VPN 连接错误");
            } finally {
                try {
                    if (trojanConnection != null) {
                        trojanConnection.disconnect();
                    }
                    if (vpnInput != null) vpnInput.close();
                    if (vpnOutput != null) vpnOutput.close();
                } catch (Exception e) {
                    Log.e(TAG, "关闭VPN流失败: " + e.getMessage());
                }
                Log.i(TAG, "VPN线程已结束");
            }
        }
    }
}   