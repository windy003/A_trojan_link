package com.example.trojanvpn;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrojanConnection {
    private static final String TAG = "TrojanConnection";
    
    private final String server;
    private final int port;
    private final String password;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ExecutorService executorService;
    private boolean isConnected = false;
    
    public TrojanConnection(String server, int port, String password) {
        this.server = server;
        this.port = port;
        this.password = password;
        this.executorService = Executors.newSingleThreadExecutor();
        connect();
    }
    
    private void connect() {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // 创建与Trojan服务器的连接
                    socket = new Socket(server, port);
                    socket.setKeepAlive(true);
                    
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                    
                    // 发送Trojan协议头部
                    String trojanHeader = password + "\r\n";
                    outputStream.write(trojanHeader.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    
                    isConnected = true;
                    Log.i(TAG, "已连接到Trojan服务器: " + server + ":" + port);
                    
                } catch (IOException e) {
                    Log.e(TAG, "连接Trojan服务器失败: " + e.getMessage());
                    disconnect();
                }
            }
        });
    }
    
    public void disconnect() {
        isConnected = false;
        try {
            if (socket != null) socket.close();
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "关闭Trojan连接失败: " + e.getMessage());
        }
    }
    
    public byte[] processOutgoing(byte[] data, int length) {
        try {
            if (!isConnected) {
                return null;
            }
            
            // 将VPN数据发送到Trojan服务器
            outputStream.write(data, 0, length);
            outputStream.flush();
            
            // 读取从Trojan服务器返回的数据
            byte[] response = new byte[4096];
            int bytesRead = inputStream.read(response);
            
            if (bytesRead > 0) {
                byte[] result = new byte[bytesRead];
                System.arraycopy(response, 0, result, 0, bytesRead);
                return result;
            }
            
        } catch (IOException e) {
            Log.e(TAG, "处理VPN数据失败: " + e.getMessage());
            disconnect();
        }
        
        return null;
    }
    
    // 计算哈希值 (用于密码处理)
    private String sha224(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-224");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "计算SHA-224失败: " + e.getMessage());
            return "";
        }
    }
} 