package com.example.trojanvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
} 