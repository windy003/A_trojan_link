package com.example.trojanvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.SSLContext;
import java.security.SSLContext;
import java.security.SSLSocket;
import java.security.SSLSocketFactory;
import java.security.SSLParameters;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SNIHostName;
import android.os.AsyncTask;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_VPN_PERMISSION = 1;
    private EditText trojanLinkInput;
    private Button connectButton;
    private Button disconnectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        trojanLinkInput = findViewById(R.id.trojan_link_input);
        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String trojanLink = trojanLinkInput.getText().toString().trim();
                if (trojanLink.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入Trojan链接", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 验证Trojan链接格式
                if (!TrojanLinkParser.isValidTrojanLink(trojanLink)) {
                    Toast.makeText(MainActivity.this, "无效的Trojan链接格式", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 请求VPN权限
                Intent vpnIntent = VpnService.prepare(MainActivity.this);
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION);
                } else {
                    onActivityResult(REQUEST_VPN_PERMISSION, RESULT_OK, null);
                }
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TrojanVpnService.class);
                intent.setAction(TrojanVpnService.ACTION_DISCONNECT);
                startService(intent);
                updateUI(false);
            }
        });
        
        updateUI(false);

        // 在现有的onCreate方法中添加调试按钮
        Button testButton = new Button(this);
        testButton.setText("测试连接");
        testButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String trojanLink = trojanLinkInput.getText().toString().trim();
                if (trojanLink.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入Trojan链接", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 测试连接而不使用VPN
                new TestConnectionTask().execute(trojanLink);
            }
        });

        // 将按钮添加到界面
        LinearLayout layout = findViewById(R.id.main_layout); // 确保您的布局有此ID
        layout.addView(testButton);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN_PERMISSION && resultCode == RESULT_OK) {
            // 启动VPN服务
            String trojanLink = trojanLinkInput.getText().toString().trim();
            Intent intent = new Intent(this, TrojanVpnService.class);
            intent.setAction(TrojanVpnService.ACTION_CONNECT);
            intent.putExtra(TrojanVpnService.EXTRA_TROJAN_LINK, trojanLink);
            startService(intent);
            updateUI(true);
        } else if (requestCode == REQUEST_VPN_PERMISSION) {
            Toast.makeText(this, "VPN权限被拒绝", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateUI(boolean isConnected) {
        connectButton.setEnabled(!isConnected);
        disconnectButton.setEnabled(isConnected);
    }

    // 添加连接测试任务
    private class TestConnectionTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String trojanLink = params[0];
            TrojanLinkParser parser = new TrojanLinkParser();
            if (!parser.parse(trojanLink)) {
                return "链接解析失败";
            }
            
            try {
                String server = parser.getServer();
                int port = parser.getPort();
                String sni = parser.getSni();
                
                // 创建SSL连接
                SSLSocketFactory factory = SSLContext.getDefault().getSocketFactory();
                SSLSocket socket = (SSLSocket) factory.createSocket(server, port);
                
                // 设置SNI
                SSLParameters sslParams = socket.getSSLParameters();
                sslParams.setServerNames(Collections.singletonList(new SNIHostName(sni)));
                socket.setSSLParameters(sslParams);
                
                // 尝试建立连接
                socket.setSoTimeout(5000);
                socket.startHandshake();
                boolean isConnected = socket.isConnected();
                socket.close();
                
                if (isConnected) {
                    // 测试谷歌连接
                    URL url = new URL("https://www.google.com");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.connect();
                    int responseCode = conn.getResponseCode();
                    conn.disconnect();
                    
                    return "TLS连接成功! Google响应码: " + responseCode;
                } else {
                    return "连接失败";
                }
            } catch (Exception e) {
                return "错误: " + e.getMessage();
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
        }
    }
} 