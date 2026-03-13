package com.terra.api.realtime;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.Role;
import com.terra.api.auth.entity.RoleName;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.auth.repository.AccountSessionRepository;
import com.terra.api.auth.repository.AccountVerificationRepository;
import com.terra.api.auth.repository.RoleRepository;
import com.terra.api.notifications.repository.AccountNotificationRepository;
import com.terra.api.auth.service.VerificationTokenService;
import com.terra.api.realtime.session.RealtimeSession;
import com.terra.api.realtime.session.RealtimeSessionRepository;
import com.terra.api.realtime.session.RealtimeSessionStatus;
import com.terra.api.realtime.websocket.RealtimeHandshakeRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String PASSWORD = "Password1!";

    @LocalServerPort
    private int port;

    @Autowired
    private AccountMasterRepository accountMasterRepository;

    @Autowired
    private AccountSessionRepository accountSessionRepository;

    @Autowired
    private AccountVerificationRepository accountVerificationRepository;

    @Autowired
    private AccountNotificationRepository accountNotificationRepository;

    @Autowired
    private RealtimeSessionRepository realtimeSessionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private RealtimeHandshakeRateLimiter handshakeRateLimiter;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        realtimeSessionRepository.deleteAll();
        accountNotificationRepository.deleteAll();
        accountSessionRepository.deleteAll();
        accountVerificationRepository.deleteAll();
        accountMasterRepository.deleteAll();
        handshakeRateLimiter.resetForTests();
    }

    // Verifica que el handshake WebSocket se acepte cuando la cuenta tiene cookies validas y Origin permitido.
    @Test
    void shouldAcceptHandshakeWhenCookiesAndOriginAreValid() throws Exception {
        createVerifiedUser("ws-handshake@l2terra.online");
        SessionCookies cookies = login("ws-handshake@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();

        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);
        try {
            assertTrue(handler.awaitMessage());
            assertTrue(session.isOpen());
            assertEquals(1, realtimeSessionRepository.count());

            RealtimeSession realtimeSession = realtimeSessionRepository.findAll().get(0);
            assertEquals(RealtimeSessionStatus.OPEN, realtimeSession.getStatus());
            assertFalse(realtimeSession.getRealtimeSessionId().isBlank());
            assertFalse(realtimeSession.getOrigin().isBlank());
        } finally {
            session.close();
            assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                    .allMatch(storedSession -> storedSession.getStatus() != RealtimeSessionStatus.OPEN)));
        }
    }

    // Verifica que el handshake WebSocket se rechace cuando el Origin no coincide con la lista permitida.
    @Test
    void shouldRejectHandshakeWhenOriginIsNotAllowed() {
        createVerifiedUser("ws-origin@l2terra.online");
        SessionCookies cookies = login("ws-origin@l2terra.online");

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> connect(cookies, "http://malicious.example", new TestRealtimeClientHandler())
        );

        assertEquals(0, realtimeSessionRepository.count());
        assertTrue(exception.getCause() != null);
    }

    // Verifica que el handshake WebSocket se rechace cuando faltan las cookies de autenticacion requeridas.
    @Test
    void shouldRejectHandshakeWhenCookiesAreMissing() {
        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> connect(null, ALLOWED_ORIGIN, new TestRealtimeClientHandler())
        );

        assertEquals(0, realtimeSessionRepository.count());
        assertTrue(exception.getCause() != null);
    }

    // Verifica que logout cierre el socket asociado a la sesion refresh actual y marque la realtime session como revocada.
    @Test
    void shouldCloseCurrentWebSocketSessionAfterLogout() throws Exception {
        createVerifiedUser("ws-logout@l2terra.online");
        SessionCookies cookies = login("ws-logout@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);

        try {
            assertTrue(handler.awaitMessage());
            logout(cookies);

            assertTrue(handler.awaitClosed());
            assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                    .allMatch(storedSession -> storedSession.getStatus() == RealtimeSessionStatus.REVOKED)));
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    // Verifica que logout-all cierre todos los sockets activos de la cuenta aunque pertenezcan a sesiones refresh distintas.
    @Test
    void shouldCloseAllActiveWebSocketSessionsAfterLogoutAll() throws Exception {
        createVerifiedUser("ws-logout-all@l2terra.online");
        SessionCookies firstCookies = login("ws-logout-all@l2terra.online");
        SessionCookies secondCookies = login("ws-logout-all@l2terra.online");
        TestRealtimeClientHandler firstHandler = new TestRealtimeClientHandler();
        TestRealtimeClientHandler secondHandler = new TestRealtimeClientHandler();
        WebSocketSession firstSession = connect(firstCookies, ALLOWED_ORIGIN, firstHandler);
        WebSocketSession secondSession = connect(secondCookies, ALLOWED_ORIGIN, secondHandler);

        try {
            assertTrue(firstHandler.awaitMessage());
            assertTrue(secondHandler.awaitMessage());
            logoutAll(secondCookies);

            assertTrue(firstHandler.awaitClosed());
            assertTrue(secondHandler.awaitClosed());
            assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                    .allMatch(storedSession -> storedSession.getStatus() == RealtimeSessionStatus.REVOKED)));
            assertEquals(2, realtimeSessionRepository.count());
        } finally {
            if (firstSession.isOpen()) {
                firstSession.close();
            }
            if (secondSession.isOpen()) {
                secondSession.close();
            }
        }
    }

    // Verifica que reset-password invalide sesiones HTTP y cierre todos los sockets activos de la cuenta inmediatamente.
    @Test
    void shouldCloseAllActiveWebSocketSessionsAfterPasswordReset() throws Exception {
        AccountMaster accountMaster = createVerifiedUser("ws-reset@l2terra.online");
        SessionCookies firstCookies = login("ws-reset@l2terra.online");
        SessionCookies secondCookies = login("ws-reset@l2terra.online");
        TestRealtimeClientHandler firstHandler = new TestRealtimeClientHandler();
        TestRealtimeClientHandler secondHandler = new TestRealtimeClientHandler();
        WebSocketSession firstSession = connect(firstCookies, ALLOWED_ORIGIN, firstHandler);
        WebSocketSession secondSession = connect(secondCookies, ALLOWED_ORIGIN, secondHandler);

        try {
            assertTrue(firstHandler.awaitMessage());
            assertTrue(secondHandler.awaitMessage());
            resetPassword(accountMaster, "AnotherPass1!");

            assertTrue(firstHandler.awaitClosed());
            assertTrue(secondHandler.awaitClosed());
            assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                    .allMatch(storedSession -> storedSession.getStatus() == RealtimeSessionStatus.REVOKED)));
        } finally {
            if (firstSession.isOpen()) {
                firstSession.close();
            }
            if (secondSession.isOpen()) {
                secondSession.close();
            }
        }
    }

    // Verifica que el servidor acepte system.pong del cliente como evento permitido sin cerrar el socket.
    @Test
    void shouldKeepConnectionOpenWhenClientSendsSystemPong() throws Exception {
        createVerifiedUser("ws-pong@l2terra.online");
        SessionCookies cookies = login("ws-pong@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);

        try {
            assertTrue(handler.awaitMessage());
            session.sendMessage(new TextMessage("""
                    {"type":"system.pong","version":1,"data":{"receivedAt":"2026-03-12T00:00:00Z"}}
                    """));

            Thread.sleep(300);
            assertTrue(session.isOpen());
            assertFalse(handler.wasClosed());
        } finally {
            session.close();
        }
    }

    // Verifica que el servidor cierre el socket cuando el cliente envía un evento que no está permitido por el protocolo.
    @Test
    void shouldCloseConnectionWhenClientSendsUnsupportedEvent() throws Exception {
        createVerifiedUser("ws-invalid-event@l2terra.online");
        SessionCookies cookies = login("ws-invalid-event@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);

        try {
            assertTrue(handler.awaitMessage());
            session.sendMessage(new TextMessage("""
                    {"type":"system.shutdown","version":1,"data":{"reason":"client_should_not_send_this"}}
                    """));

            assertTrue(handler.awaitClosed());
            assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                    .allMatch(storedSession -> storedSession.getStatus() != RealtimeSessionStatus.OPEN)));
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    // Verifica que el servidor acepte notification.ack del cliente como mensaje valido sin cerrar la sesion.
    @Test
    void shouldKeepConnectionOpenWhenClientSendsNotificationAck() throws Exception {
        createVerifiedUser("ws-ack@l2terra.online");
        SessionCookies cookies = login("ws-ack@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);

        try {
            assertTrue(handler.awaitMessage());
            session.sendMessage(new TextMessage("""
                    {"type":"notification.ack","version":1,"data":{"notificationId":"notif_123"}}
                    """));

            Thread.sleep(300);
            assertTrue(session.isOpen());
            assertFalse(handler.wasClosed());
        } finally {
            session.close();
        }
    }

    // Verifica que el servidor cierre la sesion cuando el cliente envia un payload mayor al limite permitido.
    @Test
    void shouldCloseConnectionWhenClientSendsTooLargePayload() throws Exception {
        createVerifiedUser("ws-large@l2terra.online");
        SessionCookies cookies = login("ws-large@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);

        try {
            assertTrue(handler.awaitMessage());
            String oversizedPayload = "{\"type\":\"notification.ack\",\"version\":1,\"data\":{\"blob\":\"" + "x".repeat(5000) + "\"}}";
            session.sendMessage(new TextMessage(oversizedPayload));

            assertTrue(handler.awaitClosed());
            assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                    .allMatch(storedSession -> storedSession.getStatus() != RealtimeSessionStatus.OPEN)));
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    // Verifica que el rate limit por IP rechace nuevos handshakes cuando una misma direccion supera la ventana configurada.
    @Test
    void shouldRejectHandshakeWhenIpRateLimitIsExceeded() throws Exception {
        createVerifiedUser("ws-ip-limit@l2terra.online");
        SessionCookies cookies = login("ws-ip-limit@l2terra.online");
        List<WebSocketSession> sessions = new ArrayList<>();

        try {
            for (int attempt = 0; attempt < 20; attempt++) {
                TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
                WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);
                assertTrue(handler.awaitMessage());
                sessions.add(session);
                session.close();
                assertTrue(awaitCondition(() -> realtimeSessionRepository.findAll().stream()
                        .allMatch(storedSession -> storedSession.getStatus() != RealtimeSessionStatus.OPEN)));
            }

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> connect(cookies, ALLOWED_ORIGIN, new TestRealtimeClientHandler())
            );

            assertTrue(exception.getCause() != null);
        } finally {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.close();
                }
            }
        }
    }

    // Verifica que una cuenta no pueda mantener mas conexiones WebSocket activas que el limite permitido por el servidor.
    @Test
    void shouldRejectHandshakeWhenAccountConnectionLimitIsExceeded() throws Exception {
        createVerifiedUser("ws-account-limit@l2terra.online");
        SessionCookies firstCookies = login("ws-account-limit@l2terra.online");
        SessionCookies secondCookies = login("ws-account-limit@l2terra.online");
        SessionCookies thirdCookies = login("ws-account-limit@l2terra.online");
        SessionCookies fourthCookies = login("ws-account-limit@l2terra.online");
        TestRealtimeClientHandler firstHandler = new TestRealtimeClientHandler();
        TestRealtimeClientHandler secondHandler = new TestRealtimeClientHandler();
        TestRealtimeClientHandler thirdHandler = new TestRealtimeClientHandler();
        WebSocketSession firstSession = connect(firstCookies, ALLOWED_ORIGIN, firstHandler);
        WebSocketSession secondSession = connect(secondCookies, ALLOWED_ORIGIN, secondHandler);
        WebSocketSession thirdSession = connect(thirdCookies, ALLOWED_ORIGIN, thirdHandler);

        try {
            assertTrue(firstHandler.awaitMessage());
            assertTrue(secondHandler.awaitMessage());
            assertTrue(thirdHandler.awaitMessage());

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> connect(fourthCookies, ALLOWED_ORIGIN, new TestRealtimeClientHandler())
            );

            assertTrue(exception.getCause() != null);
        } finally {
            if (firstSession.isOpen()) {
                firstSession.close();
            }
            if (secondSession.isOpen()) {
                secondSession.close();
            }
            if (thirdSession.isOpen()) {
                thirdSession.close();
            }
        }
    }

    // Verifica que crear una notificacion de prueba emita notification.created por WebSocket y sincronice el unread count.
    @Test
    void shouldReceiveNotificationCreatedEventAfterCreatingTestNotification() throws Exception {
        createVerifiedUser("ws-notification-created@l2terra.online");
        SessionCookies cookies = login("ws-notification-created@l2terra.online");
        TestRealtimeClientHandler handler = new TestRealtimeClientHandler();
        WebSocketSession session = connect(cookies, ALLOWED_ORIGIN, handler);

        try {
            assertTrue(handler.awaitMessage());
            createTestNotification(cookies);

            assertTrue(awaitCondition(() -> handler.hasPayloadContaining("\"type\":\"notification.created\"")));
            assertTrue(awaitCondition(() -> handler.hasPayloadContaining("\"type\":\"notification.unread_count\"")));
            assertTrue(awaitCondition(() -> handler.hasPayloadContaining("\"type\":\"system.test_notification\"")));
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    private AccountMaster createVerifiedUser(String email) {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("USER role not found"));

        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(email);
        accountMaster.setPasswordHash(passwordEncoder.encode(PASSWORD));
        accountMaster.setEmailVerified(true);
        accountMaster.setEnabled(true);
        accountMaster.setRoles(Set.of(userRole));
        return accountMasterRepository.save(accountMaster);
    }

    private SessionCookies login(String email) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl("/login")))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, PASSWORD)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return SessionCookies.fromHeaders(response.headers().allValues(HttpHeaders.SET_COOKIE));
        } catch (Exception exception) {
            throw new IllegalStateException("Login request failed", exception);
        }
    }

    private void logout(SessionCookies cookies) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl("/logout")))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.COOKIE, cookies.toCookieHeader())
                .header("X-CSRF-TOKEN", cookies.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Logout request failed", exception);
        }
    }

    private void logoutAll(SessionCookies cookies) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl("/logout-all")))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.COOKIE, cookies.toCookieHeader())
                .header("X-CSRF-TOKEN", cookies.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Logout all request failed", exception);
        }
    }

    private void resetPassword(AccountMaster accountMaster, String newPassword) {
        String resetToken = verificationTokenService.createOrRefresh(accountMaster, com.terra.api.auth.entity.AccountVerificationType.PASSWORD_RESET, 15L);
        HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl("/reset-password")))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "token": "%s",
                          "newPassword": "%s"
                        }
                        """.formatted(resetToken, newPassword)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Reset password request failed", exception);
        }
    }

    private void createTestNotification(SessionCookies cookies) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(testNotificationUrl()))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "email": "ws-notification-created@l2terra.online",
                          "template": "system.test_notification",
                          "params": {}
                        }
                        """))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Create test notification request failed", exception);
        }
    }

    private WebSocketSession connect(SessionCookies cookies,
                                     String origin,
                                     TestRealtimeClientHandler handler) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.ORIGIN, origin);
        if (cookies != null) {
            headers.add(HttpHeaders.COOKIE, cookies.toWebSocketCookieHeader());
        }

        StandardWebSocketClient client = new StandardWebSocketClient();
        CompletableFuture<WebSocketSession> future = client.execute(
                handler,
                headers,
                URI.create("ws://localhost:" + port + "/api/ws")
        );

        return future.get(5, TimeUnit.SECONDS);
    }

    private String authUrl(String path) {
        return "http://localhost:" + port + "/api/auth" + path;
    }

    private String notificationUrl(String path) {
        return "http://localhost:" + port + "/api/notifications" + path;
    }

    private String testNotificationUrl() {
        return "http://localhost:" + port + "/api/test/notifications";
    }

    private boolean awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private record SessionCookies(String accessToken, String refreshToken, String csrfToken) {
        static SessionCookies fromHeaders(List<String> setCookieHeaders) {
            String accessToken = null;
            String refreshToken = null;
            String csrfToken = null;

            for (String headerValue : setCookieHeaders) {
                String[] parts = headerValue.split(";\\s*");
                String[] nameAndValue = parts[0].split("=", 2);
                if (nameAndValue.length < 2) {
                    continue;
                }

                switch (nameAndValue[0]) {
                    case "terra_access_token" -> accessToken = nameAndValue[1];
                    case "terra_refresh_token" -> refreshToken = nameAndValue[1];
                    case "XSRF-TOKEN" -> csrfToken = nameAndValue[1];
                    default -> {
                    }
                }
            }

            if (accessToken == null || refreshToken == null || csrfToken == null) {
                throw new IllegalStateException("Missing auth cookies after login");
            }

            return new SessionCookies(accessToken, refreshToken, csrfToken);
        }

        String toCookieHeader() {
            List<String> cookies = new ArrayList<>();
            cookies.add("terra_access_token=" + accessToken);
            cookies.add("terra_refresh_token=" + refreshToken);
            cookies.add("XSRF-TOKEN=" + csrfToken);
            return String.join("; ", cookies);
        }

        String toWebSocketCookieHeader() {
            return String.join("; ",
                    "terra_access_token=" + accessToken,
                    "terra_refresh_token=" + refreshToken
            );
        }
    }

    private static final class TestRealtimeClientHandler extends TextWebSocketHandler {
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.CountDownLatch messageLatch = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch closeLatch = new java.util.concurrent.CountDownLatch(1);
        private volatile CloseStatus closeStatus;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            payloads.add(message.getPayload());
            messageLatch.countDown();
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            this.closeStatus = status;
            closeLatch.countDown();
        }

        boolean awaitMessage() throws InterruptedException {
            return messageLatch.await(5, TimeUnit.SECONDS);
        }

        boolean awaitClosed() throws InterruptedException {
            return closeLatch.await(5, TimeUnit.SECONDS);
        }

        boolean hasPayloadContaining(String fragment) {
            return payloads.stream().anyMatch(payload -> payload.contains(fragment));
        }

        boolean wasClosed() {
            return closeStatus != null;
        }
    }
}
