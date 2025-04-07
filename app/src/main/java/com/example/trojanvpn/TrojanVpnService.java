package com.example.trojanvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrojanVpnService extends VpnService {
    private static final String TAG = "TrojanVpnService";
    
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
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONNECT.equals(intent.getAction())) {
            String trojanLink = intent.getStringExtra(EXTRA_TROJAN_LINK);
            if (trojanLink != null) {
                connectVpn(trojanLink);
            }
        } else if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnectVpn();
        }
        
        return START_STICKY;
    }
    
    private void connectVpn(String trojanLink) {
        if (isRunning) {
            return;
        }
        
        // 解析Trojan链接
        TrojanLinkParser parser = new TrojanLinkParser();
        if (!parser.parse(trojanLink)) {
            Log.e(TAG, "解析Trojan链接失败");
            return;
        }
        
        // 配置VPN
        try {
            // 构建VPN接口
            Builder builder = new Builder()
                    .setSession("TrojanVPN")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .setMtu(1500);
            
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "创建VPN接口失败");
                return;
            }
            
            isRunning = true;
            
            // 启动VPN处理线程
            executorService.submit(new VpnRunnable(vpnInterface, parser));
            
        } catch (Exception e) {
            Log.e(TAG, "启动VPN失败: " + e.getMessage());
            disconnectVpn();
        }
    }
    
    private void disconnectVpn() {
        isRunning = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
            } catch (Exception e) {
                Log.e(TAG, "关闭VPN接口失败: " + e.getMessage());
            }
        }
        stopSelf();
    }
    
    @Override
    public void onDestroy() {
        disconnectVpn();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }
    
    // VPN处理线程
    private class VpnRunnable implements Runnable {
        private final ParcelFileDescriptor vpnInterface;
        private final TrojanLinkParser config;
        
        public VpnRunnable(ParcelFileDescriptor vpnInterface, TrojanLinkParser config) {
            this.vpnInterface = vpnInterface;
            this.config = config;
        }
        
        @Override
        public void run() {
            FileInputStream vpnInput = null;
            FileOutputStream vpnOutput = null;
            
            try {
                vpnInput = new FileInputStream(vpnInterface.getFileDescriptor());
                vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor());
                
                // 连接到Trojan服务器
                // 注意：此处应集成实际的Trojan协议实现
                // 这里只是一个简化的示例框架
                TrojanConnection trojanConnection = new TrojanConnection(
                        config.getServer(),
                        config.getPort(),
                        config.getPassword());
                
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
                        if (processedData != null) {
                            vpnOutput.write(processedData);
                        }
                        
                        packet.clear();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "VPN处理错误: " + e.getMessage());
            } finally {
                try {
                    if (vpnInput != null) vpnInput.close();
                    if (vpnOutput != null) vpnOutput.close();
                } catch (Exception e) {
                    Log.e(TAG, "关闭VPN流失败: " + e.getMessage());
                }
            }
        }
    }
} 