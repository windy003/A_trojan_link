package com.example.trojanvpn;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SNIHostName;

public class TrojanConnection {
    private static final String TAG = "TrojanConnection";
    
    private final String server;
    private final int port;
    private final String password;
    private final String sni;
    private final boolean allowInsecure;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ExecutorService executorService;
    private boolean isConnected = false;
    
    public TrojanConnection(String server, int port, String password) {
        this(server, port, password, null, false);
    }
    
    public TrojanConnection(String server, int port, String password, String sni, boolean allowInsecure) {
        this.server = server;
        this.port = port;
        this.password = password;
        this.sni = sni == null || sni.isEmpty() ? server : sni;
        this.allowInsecure = allowInsecure;
        this.executorService = Executors.newSingleThreadExecutor();
        connect();
    }
    
    private void connect() {
        executorService.submit(() -> {
            try {
                Log.i(TAG, "正在连接到Trojan服务器: " + server + ":" + port);
                
                // 创建SSLSocketFactory用于TLS加密
                SSLContext sslContext = SSLContext.getInstance("TLS");
                if (allowInsecure) {
                    sslContext.init(null, createTrustAllCerts(), new SecureRandom());
                } else {
                    sslContext.init(null, null, new SecureRandom());
                }
                SSLSocketFactory factory = sslContext.getSocketFactory();
                
                // 创建与服务器的加密连接
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(server, port);
                
                // 设置SNI扩展
                SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setServerNames(Collections.singletonList(new SNIHostName(sni)));
                sslSocket.setSSLParameters(sslParams);
                
                socket = sslSocket;
                socket.setKeepAlive(true);
                
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                
                // 发送Trojan协议头部
                String hexPassword = sha224(password);
                outputStream.write((hexPassword + "\r\n").getBytes(StandardCharsets.UTF_8));
                
                // SOCKS5 CMD格式: CONNECT方式
                byte[] socksHeader = new byte[] {
                    0x05, // SOCKS5版本
                    0x01, // CMD: CONNECT
                    0x00, // 保留字段
                    0x01  // 地址类型: IPv4
                };
                outputStream.write(socksHeader);
                
                // 写入一个虚拟目标地址(后续会被VPN服务处理)
                byte[] dummyAddr = new byte[] {
                    0x01, 0x01, 0x01, 0x01,  // IP: 1.1.1.1
                    0x01, 0x01               // 端口: 257
                };
                outputStream.write(dummyAddr);
                outputStream.flush();
                
                // 连接建立完成
                isConnected = true;
                Log.i(TAG, "已成功连接到Trojan服务器: " + server + ":" + port);
                
            } catch (Exception e) {
                Log.e(TAG, "连接Trojan服务器失败: " + e.getMessage(), e);
                disconnect();
            }
        });
    }
    
    private TrustManager[] createTrustAllCerts() {
        return new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };
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
                Log.e(TAG, "尝试发送数据但未连接到服务器");
                return null;
            }
            
            // 将VPN数据发送到Trojan服务器
            outputStream.write(data, 0, length);
            outputStream.flush();
            Log.d(TAG, "发送了 " + length + " 字节到Trojan服务器");
            
            // 尝试读取从Trojan服务器返回的数据
            byte[] response = new byte[4096];
            int bytesRead = -1;
            
            try {
                bytesRead = inputStream.read(response);
            } catch (IOException e) {
                Log.e(TAG, "从服务器读取数据失败: " + e.getMessage());
                reconnect();
                return null;
            }
            
            if (bytesRead > 0) {
                Log.d(TAG, "从Trojan服务器接收了 " + bytesRead + " 字节");
                byte[] result = new byte[bytesRead];
                System.arraycopy(response, 0, result, 0, bytesRead);
                return result;
            } else if (bytesRead == -1) {
                Log.e(TAG, "服务器连接已关闭");
                reconnect();
            }
            
        } catch (IOException e) {
            Log.e(TAG, "处理VPN数据失败: " + e.getMessage());
            reconnect();
        }
        
        return null;
    }
    
    private void reconnect() {
        disconnect();
        connect();
    }
    
    // 计算SHA-224哈希值并转为十六进制字符串
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