package com.kaisoundgood.feedbackassist;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainActivity extends Activity {
    private TextView status;
    private volatile boolean probing = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11,16,22));
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(12,8,12,8);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setText("RTA NOT VERIFIED · OSC read-only · 192.168.0.100");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        bar.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        Button probe = new Button(this);
        probe.setText("TEST OSC + RTA");
        probe.setOnClickListener(v -> runProbe());
        bar.addView(probe);
        root.addView(bar);
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.rgb(11,16,22));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setDomStorageEnabled(false);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private static byte[] oscQuery(String path) {
        byte[] raw = (path + "\u0000").getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(raw,(raw.length+3)&~3);
    }

    private static byte[] oscString(String value) {
        byte[] raw = (value + "\u0000").getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(raw,(raw.length+3)&~3);
    }

    private void update(String text) { runOnUiThread(() -> status.setText(text)); }

    private void runProbe() {
        if (probing) return;
        probing = true;
        update("Testing OSC read-only...");
        new Thread(() -> {
            try (DatagramSocket sock = new DatagramSocket()) {
                sock.setSoTimeout(1800);
                InetAddress mixer = InetAddress.getByName("192.168.0.100");
                byte[] request = oscQuery("/xinfo");
                sock.send(new DatagramPacket(request,request.length,mixer,10023));
                byte[] buf = new byte[65535];
                DatagramPacket reply = new DatagramPacket(buf,buf.length);
                sock.receive(reply);
                if (!reply.getAddress().equals(mixer)) {
                    update("Ignored packet from unexpected address");
                    return;
                }
                String payload = new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8);
                String address = payload.split("\u0000",2)[0];
                update("OSC: "+address+" · probing meter stream (read-only)...");
                // Query only. No source selection and no EQ writes.
                byte[] sourceQuery = oscQuery("/-prefs/rta/source");
                sock.send(new DatagramPacket(sourceQuery,sourceQuery.length,mixer,10023));
                // OSC /meters with a single string argument naming the requested stream.
                for (String stream : new String[]{"/meters/15","/meters/16"}) {
                    byte[] streamBytes = oscString(stream);
                    byte[] header = oscQuery("/meters");
                    byte[] typeTags = oscString(",s");
                    byte[] subscribe = new byte[header.length+typeTags.length+streamBytes.length];
                    System.arraycopy(header,0,subscribe,0,header.length);
                    System.arraycopy(typeTags,0,subscribe,header.length,typeTags.length);
                    System.arraycopy(streamBytes,0,subscribe,header.length+typeTags.length,streamBytes.length);
                    sock.send(new DatagramPacket(subscribe,subscribe.length,mixer,10023));
                }
                long end = android.os.SystemClock.elapsedRealtime()+2500;
                int meterPackets=0;
                int otherPackets=0;
                sock.setSoTimeout(350);
                while (android.os.SystemClock.elapsedRealtime()<end) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf,buf.length);
                        sock.receive(packet);
                        if (!packet.getAddress().equals(mixer)) continue;
                        String msg = new String(packet.getData(),0,Math.min(packet.getLength(),64),StandardCharsets.UTF_8);
                        String path = msg.split("\u0000",2)[0];
                        if (path.startsWith("/meters/")) meterPackets++;
                        else otherPackets++;
                    } catch (java.net.SocketTimeoutException ignored) { }
                }
                update("OSC OK · meter packets: "+meterPackets+" · other: "+otherPackets+" · RTA unverified");
            } catch (Exception e) {
                update("OSC test failed: "+e.getClass().getSimpleName()+" · check mixer/network");
            } finally { probing = false; }
        }).start();
    }
}
