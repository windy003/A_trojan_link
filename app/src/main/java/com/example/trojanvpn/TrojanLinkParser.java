package com.example.trojanvpn;

import android.net.Uri;
import android.util.Log;

import java.util.regex.Pattern;

public class TrojanLinkParser {
    private static final String TAG = "TrojanLinkParser";
    
    // Trojan链接格式: trojan://password@server:port#remark
    private static final Pattern TROJAN_LINK_PATTERN = 
            Pattern.compile("^trojan://([^@]+)@([^:]+):(\\d+)(#.+)?$");
    
    private String password;
    private String server;
    private int port;
    private String remark;
    
    public static boolean isValidTrojanLink(String link) {
        return TROJAN_LINK_PATTERN.matcher(link).matches();
    }
    
    public boolean parse(String trojanLink) {
        try {
            if (!trojanLink.startsWith("trojan://")) {
                Log.e(TAG, "不是有效的Trojan链接");
                return false;
            }
            
            // 移除前缀
            String schemeSpecificPart = trojanLink.substring(9);
            
            // 解析备注（如果有）
            String linkWithoutRemark = schemeSpecificPart;
            remark = "";
            int sharpIndex = schemeSpecificPart.indexOf('#');
            if (sharpIndex > 0) {
                linkWithoutRemark = schemeSpecificPart.substring(0, sharpIndex);
                remark = schemeSpecificPart.substring(sharpIndex + 1);
            }
            
            // 解析密码、服务器和端口
            String[] parts = linkWithoutRemark.split("@");
            if (parts.length != 2) {
                Log.e(TAG, "链接格式错误");
                return false;
            }
            
            password = Uri.decode(parts[0]);
            
            String[] serverParts = parts[1].split(":");
            if (serverParts.length != 2) {
                Log.e(TAG, "服务器格式错误");
                return false;
            }
            
            server = serverParts[0];
            try {
                port = Integer.parseInt(serverParts[1]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "端口无效");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "解析错误: " + e.getMessage());
            return false;
        }
    }
    
    public String getPassword() {
        return password;
    }
    
    public String getServer() {
        return server;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getRemark() {
        return remark;
    }
} 