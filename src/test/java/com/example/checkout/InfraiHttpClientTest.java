package com.example.checkout;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class InfraiHttpClientTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger attempts = new AtomicInteger();
        long[] first = new long[1];
        server.createContext("/", exchange -> {
            String body;
            int status = 200;
            if (exchange.getRequestMethod().equals("POST")) {
                if (attempts.getAndIncrement() == 0) {
                    first[0] = System.nanoTime();
                    exchange.getResponseHeaders().set("Retry-After", "1");
                    status = 429;
                    body = "{\"ok\": false, \"error\": {}}";
                } else {
                    if (System.nanoTime() - first[0] < 950_000_000L) throw new AssertionError("Retry-After ignored");
                    body = "{\"ok\" : true, \"data\" : {\"pdf_base64\" : \"JVBERi0=\"}}";
                }
            } else {
                if (!exchange.getRequestURI().getRawPath().endsWith("/bucket%20one/receipts%2Fa%2Bb.pdf"))
                    throw new AssertionError("incorrect path encoding: " + exchange.getRequestURI());
                body = "{\"ok\" : false, \"error\" : {\"message\":\"rejected\"}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            InfraiHttpClient client = new InfraiHttpClient("test", "http://127.0.0.1:" + server.getAddress().getPort());
            if (!client.fillForm("JVBERi0=", "{}").equals("JVBERi0=")) throw new AssertionError("PDF parsing failed");
            try {
                client.archive("bucket one", "receipts/a+b.pdf", "JVBERi0=");
                throw new AssertionError("accepted rejected envelope");
            } catch (java.io.IOException expected) {
                if (!expected.getMessage().contains("rejected")) throw expected;
            }
            System.out.println("HTTP regression checks passed");
        } finally { server.stop(0); }
    }
}
