package mk.ukim.finki.testing.booking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class StubRegistryServer {

    @FunctionalInterface
    interface Behavior {
        void handle(HttpExchange exchange) throws IOException, InterruptedException;
    }

    private final HttpServer server;
    private final ExecutorService executor;
    // Zapisuva sekoe baranje kako "GET /members/C-01" - za da proverime sto tocno isprakja consumer-ot
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile Behavior behavior = StubRegistryServer::provider;
    // Latch so koj se "drzat" odgovorite vo delayResponse() dodeka testot ne ja povika reset()/stop()
    private volatile CountDownLatch gate = new CountDownLatch(1);

    private StubRegistryServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    static StubRegistryServer start() {
        try {
            // 127.0.0.1 (ne "localhost") za da nema razliki IPv4/IPv6; port 0 = slobodna porta
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "stub-registry-worker");
                thread.setDaemon(true);
                return thread;
            });
            StubRegistryServer stub = new StubRegistryServer(server, executor);
            server.createContext("/", stub::dispatch);
            server.setExecutor(executor);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException("Ne moze da se startuva stub registry serverot", e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<String> requests() {
        return List.copyOf(requests);
    }

    /** Vrakja se na "zdrav" provider i go cisti zapisot na baranja; gi osloboduva i zadrzanite odgovori. */
    void reset() {
        gate.countDown();
        gate = new CountDownLatch(1);
        requests.clear();
        behavior = StubRegistryServer::provider;
    }

    void stop() {
        gate.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    // --- Ponasuvanja za simulacija na greshki ---

    /** Sekoe baranje dobiva odgovor so zadadeni status, content-type i telo. */
    void respondWith(int status, String contentType, String body) {
        behavior = exchange -> send(exchange, status, contentType, body);
    }

    void respondJson(int status, String body) {
        respondWith(status, "application/json", body);
    }

    /** Konekcijata se zatvora bez nikakov odgovor (kako server koj padnal sredina na baranje). */
    void dropConnection() {
        behavior = exchange -> { /* ne isprakjame nisto; dispatch() ja zatvora konekcijata */ };
    }

    /** Odgovorot kasni (do 10s) - klientot treba da istekne so read timeout mnogu pred toa. */
    void delayResponse() {
        behavior = exchange -> {
            gate.await(10, TimeUnit.SECONDS);
            provider(exchange);
        };
    }

    // --- Interno ---

    private void dispatch(HttpExchange exchange) {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        try {
            behavior.handle(exchange);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // klientot (so istecen timeout) veke ja zatvoril konekcijata - ocekuvano vo testovite za timeout
        } finally {
            exchange.close();
        }
    }

    /** Podrazbirano ponasuvanje = dogovorot na vistinskiot provider opisan vo README (C-01 da, C-02 ne, ostanato 404). */
    private static void provider(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String memberId = path.startsWith("/members/") ? path.substring("/members/".length()) : "";
        if ("C-01".equals(memberId)) {
            send(exchange, 200, "application/json", "{\"memberId\":\"C-01\",\"eligible\":true}");
        } else if ("C-02".equals(memberId)) {
            send(exchange, 200, "application/json", "{\"memberId\":\"C-02\",\"eligible\":false}");
        } else {
            send(exchange, 404, "application/json", "{\"code\":\"MEMBER_NOT_FOUND\"}");
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // -1 = nema telo (prazen odgovor)
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
