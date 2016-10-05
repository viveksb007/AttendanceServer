package com.viveksb007.attendenceserver;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final String TAG = getClass().getSimpleName();
    public WifiManager mWifiManager;
    Button startHotspot, stopHotspot;
    TextView tvIPinfo, socketInfo, clientInfo;
    ServerSocket serverSocket;
    String msg = "", messageFromClient = "";
    Thread socketServerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startHotspot = (Button) findViewById(R.id.button);
        stopHotspot = (Button) findViewById(R.id.button2);
        tvIPinfo = (TextView) findViewById(R.id.tvIPinfo);
        socketInfo = (TextView) findViewById(R.id.tvSocketInfo);
        clientInfo = (TextView) findViewById(R.id.tvClientInfo);

        startHotspot.setOnClickListener(this);
        stopHotspot.setOnClickListener(this);

        mWifiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button:
                turnOnWifiHotspot();
                startServer();
                while (getIpAddress().equals("")) {
                }
                tvIPinfo.setText(getIpAddress());
                break;
            case R.id.button2:
                stopServer();
                turnOffWifiHotspot();
                tvIPinfo.setText("");
                break;
        }
    }

    private void stopServer() {
        if (serverSocket != null) {
            try {
                socketServerThread.interrupt();
                serverSocket.close();
                Toast.makeText(MainActivity.this, "ServerSocket CLOSED", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void turnOffWifiHotspot() {
        Method setWifiAPMethod;
        try {
            setWifiAPMethod = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            boolean apStatus = (Boolean) setWifiAPMethod.invoke(mWifiManager, null, false);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void turnOnWifiHotspot() {

        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }

        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = "AttendenceServer";
        wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        try {
            Method setWifiAPMethod = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            boolean apStatus = (Boolean) setWifiAPMethod.invoke(mWifiManager, wifiConfiguration, true);

            Method isWifiAPEnabled = mWifiManager.getClass().getMethod("isWifiApEnabled");

            Method getWifiAPStateMethod = mWifiManager.getClass().getMethod("getWifiApState");
            int apState = (Integer) getWifiAPStateMethod.invoke(mWifiManager);

            Method getWifiAPConfigMethod = mWifiManager.getClass().getMethod("getWifiApConfiguration");
            wifiConfiguration = (WifiConfiguration) getWifiAPConfigMethod.invoke(mWifiManager);

            Log.v("Client", "SSID\n" + wifiConfiguration.SSID + "\nPassword : " + wifiConfiguration.preSharedKey);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

    }

    private void startServer() {
        socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverSocket != null) {
            try {
                socketServerThread.interrupt();
                serverSocket.close();
                Toast.makeText(MainActivity.this, "ServerSocket CLOSED", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SocketServerThread extends Thread {
        static final int SOCKET_SERVER_PORT = 8080;

        @Override
        public void run() {
            Socket socket = null;
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;
            String macAddresses = "";
            try {
                serverSocket = new ServerSocket(SOCKET_SERVER_PORT);

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        socketInfo.setText("I am Waiting @ " + serverSocket.getLocalPort());
                    }
                });

                while (true) {
                    socket = serverSocket.accept();
                    dataInputStream = new DataInputStream(socket.getInputStream());
                    dataOutputStream = new DataOutputStream(socket.getOutputStream());

                    messageFromClient = dataInputStream.readUTF();
                    macAddresses = macAddresses + messageFromClient + "\n";

                    final String finalMacAddressess = macAddresses;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            clientInfo.setText(finalMacAddressess);
                        }
                    });

                    if ((messageFromClient == null) | (messageFromClient == "")) {
                        dataOutputStream.writeUTF("404");
                    } else {
                        dataOutputStream.writeUTF("200");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {

                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }


    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "SiteLocalAddress: "
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }


}
