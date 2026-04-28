package com.example.vrdesert;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MoveServer extends Thread {
    private ServerSocket serverSocket;
    private boolean running = true;
    private RemoteControlListener listener;

    public MoveServer(RemoteControlListener listener) {
        this.listener = listener;
    }

    public void setListener(RemoteControlListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(8080);
            while (running) {
                Socket client = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String requestLine = in.readLine();
                
                if (requestLine != null) {
                    android.util.Log.d("MoveServer", "Request: " + requestLine);
                    OutputStream out = client.getOutputStream();
                    
                    if (requestLine.contains("GET /control")) {
                        String html = "<html><head>" +
                            "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0'>" +
                            "<style>" +
                            "body { background: #111; margin: 0; padding: 0; height: 100vh; width: 100vw; display: flex; align-items: center; justify-content: center; touch-action: none; overflow: hidden; }" +
                            "h1 { color: rgba(255,255,255,0.7); font-family: sans-serif; font-size: 10vw; user-select: none; pointer-events: none; text-align: center; font-weight: bold;}" +
                            "</style></head><body>" +
                            "<h1>TAP TO MOVE</h1>" +
                            "<script>" +
                            "function doMove() {" +
                            "  document.body.style.background = '#00AA00';" +
                            "  fetch('/move/click').catch(err => alert('Error: ' + err));" +
                            "  setTimeout(()=>document.body.style.background = '#111', 150);" +
                            "}" +
                            "document.body.addEventListener('touchstart', (e) => { e.preventDefault(); doMove(); }, {passive: false});" +
                            "document.body.addEventListener('click', (e) => { doMove(); });" +
                            "</script></body></html>";

                        String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " + html.getBytes().length + "\r\nConnection: close\r\n\r\n" + html;
                        out.write(response.getBytes());
                    } 
                    else if (requestLine.contains("GET /move/click")) {
                        android.util.Log.d("MoveServer", "Remote Move Click Received!");
                        if (listener != null) {
                            listener.onRemoteClick();
                        }
                        
                        String response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                        out.write(response.getBytes());
                    }
                    else {
                        String response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                        out.write(response.getBytes());
                    }
                    
                    out.flush();
                }
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {}
    }
}
