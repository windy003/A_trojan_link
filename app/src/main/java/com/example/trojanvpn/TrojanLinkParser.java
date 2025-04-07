package com.example.trojanvpn;

import android.net.Uri;
import android.util.Log;

import java.util.regex.Pattern;

public class TrojanLinkParser {
    private static final String TAG = "TrojanLinkParser";
    
    // 更新的正则表达式，考虑查询参数
    private static final Pattern TROJAN_LINK_PATTERN = 
            Pattern.compile("^trojan://([^@]+)@([^:]+):(\\d+)(\\?[^#]*)?(#.+)?$");
    
    private String password;
    private String server;
    private int port;
    private String remark;
    private String queryParams;
    
    public static boolean isValidTrojanLink(String link) {
        return TROJAN_LINK_PATTERN.matcher(link).matches();
    }
    
    public boolean parse(String trojanLink) {
        try {
            if (!trojanLink.startsWith("trojan://")) {
                Log.e(TAG, "不是有效的Trojan链接");
                return false;
            }
            
            // 使用Uri解析整个链接
            Uri uri = Uri.parse(trojanLink);
            
            // 检查scheme
            if (!"trojan".equals(uri.getScheme())) {
                Log.e(TAG, "协议不是trojan");
                return false;
            }
            
            // 获取用户信息（密码）
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isEmpty()) {
                Log.e(TAG, "缺少密码");
                return false;
            }
            password = Uri.decode(userInfo);
            
            // 获取服务器地址
            server = uri.getHost();
            if (server == null || server.isEmpty()) {
                Log.e(TAG, "缺少服务器地址");
                return false;
            }
            
            // 获取端口
            port = uri.getPort();
            if (port == -1) {
                Log.e(TAG, "缺少端口");
                return false;
            }
            
            // 获取查询参数
            queryParams = uri.getQuery();
            
            // 获取备注（片段）
            remark = uri.getFragment();
            if (remark == null) {
                remark = "";
            }
            
            Log.i(TAG, "成功解析Trojan链接: 服务器=" + server + ", 端口=" + port + 
                  ", SNI=" + getSni() + ", 备注=" + remark);
            
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
    
    public String getQueryParams() {
        return queryParams;
    }
    
    public String getPeer() {
        if (queryParams == null) return null;
        Uri uri = Uri.parse("trojan://dummy?" + queryParams);
        return uri.getQueryParameter("peer");
    }
    
    public String getSni() {
        if (queryParams == null) return server; // 默认使用服务器作为SNI
        Uri uri = Uri.parse("trojan://dummy?" + queryParams);
        String sni = uri.getQueryParameter("sni");
        return sni != null ? sni : server;  // 如果没有指定SNI，使用服务器地址
    }
    
    public boolean getAllowInsecure() {
        if (queryParams == null) return false;
        Uri uri = Uri.parse("trojan://dummy?" + queryParams);
        String value = uri.getQueryParameter("allowInsecure");
        return "true".equals(value);
    }
} 