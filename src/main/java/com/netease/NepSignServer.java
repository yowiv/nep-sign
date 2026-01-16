package com.netease;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 网易大神签名 HTTP 服务
 * 
 * API:
 * POST /sign
 *   请求体: {"url": "...", "content": "..."}
 *   响应: {"signed_url": "...", "success": true}
 * 
 * GET /health
 *   响应: {"status": "ok"}
 */
public class NepSignServer {
    
    private static NepSign nepSign;
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("    网易大神签名服务");
        System.out.println("========================================");
        
        // 初始化签名模块
        System.out.println("[*] 正在初始化 Unidbg...");
        try {
            nepSign = new NepSign();
            System.out.println("[+] Unidbg 初始化成功!");
        } catch (Exception e) {
            System.err.println("[-] Unidbg 初始化失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        
        // 启动 HTTP 服务
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/sign", new SignHandler());
        server.createContext("/sign_get", new SignGetHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(null);
        server.start();
        
        System.out.println("[+] HTTP 服务已启动: http://0.0.0.0:" + PORT);
        System.out.println("[*] 接口:");
        System.out.println("    POST /sign        - POST 请求签名");
        System.out.println("    POST /sign_get    - GET 请求签名");
        System.out.println("    GET  /health      - 健康检查");
        System.out.println("========================================\n");
    }
    
    // POST 签名处理
    static class SignHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置 CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            
            try {
                // 读取请求体
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                // 简单 JSON 解析
                String url = extractJsonValue(body, "url");
                String content = extractJsonValue(body, "content");
                
                if (url == null || content == null) {
                    sendError(exchange, 400, "Missing url or content");
                    return;
                }
                
                System.out.println("[*] 签名请求: " + url);
                
                // 调用签名
                String signedUrl = nepSign.getPostMethodSignatures(url, content);
                
                // 返回结果
                String response;
                if (signedUrl != null) {
                    response = "{\"success\":true,\"signed_url\":\"" + escapeJson(signedUrl) + "\"}";
                    System.out.println("[+] 签名成功");
                } else {
                    response = "{\"success\":false,\"error\":\"签名失败\"}";
                    System.out.println("[-] 签名失败");
                }
                
                sendResponse(exchange, 200, response);
                
            } catch (Exception e) {
                System.err.println("[-] 签名异常: " + e.getMessage());
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    // GET 签名处理
    static class SignGetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            
            try {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                String url = extractJsonValue(body, "url");
                
                if (url == null) {
                    sendError(exchange, 400, "Missing url");
                    return;
                }
                
                System.out.println("[*] GET签名请求: " + url);
                
                String signedUrl = nepSign.getGetMethodSignatures(url);
                
                String response;
                if (signedUrl != null) {
                    response = "{\"success\":true,\"signed_url\":\"" + escapeJson(signedUrl) + "\"}";
                } else {
                    response = "{\"success\":false,\"error\":\"签名失败\"}";
                }
                
                sendResponse(exchange, 200, response);
                
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    // 健康检查
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            sendResponse(exchange, 200, "{\"status\":\"ok\",\"service\":\"nep-sign\"}");
        }
    }
    
    // 辅助方法
    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String body = "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
        sendResponse(exchange, code, body);
    }
    
    private static String extractJsonValue(String json, String key) {
        // 简单的 JSON 值提取
        String pattern = "\"" + key + "\"\\s*:\\s*\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        
        start += pattern.length() - 1;
        int end = json.indexOf("\"", start + 1);
        
        // 处理转义引号
        while (end > 0 && json.charAt(end - 1) == '\\') {
            end = json.indexOf("\"", end + 1);
        }
        
        if (end == -1) return null;
        return json.substring(start + 1, end);
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
