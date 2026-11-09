package io.fluxzero.training.meepleverse.shop;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.fluxzero.idp.client.*;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.*;
import org.springframework.stereotype.Component;

import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static io.fluxzero.sdk.configuration.ApplicationProperties.getProperty;

/** Local, single-instance shop. Only opaque session/correlation handles go in browser cookies. */
@Component
@NoUserRequired
public class ShopLogin {
    private static final String LOGIN = "meeple_login", SESSION = "meeple_session";
    private final Cache<String, Pending> logins = Caffeine.newBuilder().maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(5)).build();
    private final ShopSessions sessions = new ShopSessions(Path.of(".fluxzero", "shop-sessions"));
    private volatile OidcClient oidc;
    private final JwksTokenValidator validator = new JwksTokenValidator();

    record Pending(String binding, String nonce, Pkce pkce) {}
    record Member(String key, String name, String csrf) {}

    private synchronized OidcClient client() {
        if (oidc == null) {
            var config = new OidcTenantConfig(getProperty("fluxzero.auth.oidc.issuer"),
                    getProperty("fluxzero.auth.oidc.client-id"), getProperty("fluxzero.auth.oidc.redirect-uri"),
                    getProperty("fluxzero.auth.oidc.resource-audience"), "openid profile email");
            oidc = new OidcClient(config);
        }
        return oidc;
    }

    @HandleGet("/shop-account/login")
    WebResponse login() {
        var client = client();
        var state = random();
        var pending = new Pending(random(), random(), Pkce.generate());
        logins.put(state, pending);
        return redirect(client.authorizationUrl(pending.pkce(), state, pending.nonce()))
                .header("Set-Cookie", cookie(LOGIN, pending.binding(), 300)).build();
    }

    @HandleGet("/app/callback")
    WebResponse callback(WebRequest request) {
        var rawQuery = URI.create(request.getPath()).getRawQuery();
        var query = FormCodec.decode(rawQuery == null ? "" : rawQuery);
        var state = query.getOrDefault("state", "");
        var pending = logins.getIfPresent(state);
        if (pending == null || !pending.binding().equals(value(request, LOGIN)) || !logins.asMap().remove(state, pending)) {
            return redirect("/shop/#/login-error").build();
        }
        try {
            var client = client();
            var tokens = client.exchangeCode(query.get("code"), pending.pkce().verifier());
            var claims = validator.validate(TokenValidationRequest.idToken(tokens.idToken(), client.config()));
            if (!pending.nonce().equals(claims.claim("nonce"))) {
                return redirect("/shop/#/login-error").build();
            }
            var handle = sessions.create(new Member(accountKey(claims.issuer(), claims.subject()), claims.subject(), random()));
            return redirect("/shop/").header("Set-Cookie", java.util.List.of(cookie(SESSION, handle, ShopSessions.LIFETIME.toSeconds()),
                    cookie(LOGIN, "", 0))).build();
        } catch (RuntimeException invalidLogin) {
            return redirect("/shop/#/login-error").build();
        }
    }

    @HandlePost("/shop-account/logout")
    WebResponse logout(WebRequest request) {
        if (writable(request) == null) return denied();
        sessions.remove(value(request, SESSION));
        return noStore().header("Set-Cookie", cookie(SESSION, "", 0))
                .payload(Map.of("next", client().endSessionUrl(getProperty("fluxzero.auth.external-base-url") + "/shop/"))).build();
    }

    Member member(WebRequest request) {
        return sessions.find(value(request, SESSION));
    }

    Member writable(WebRequest request) {
        var member = member(request);
        return member != null && member.csrf().equals(request.getHeader("X-Shop-CSRF")) ? member : null;
    }

    static String accountKey(String issuer, String subject) {
        return sha256(issuer + "\0" + subject);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String random() { return UUID.randomUUID().toString(); }
    private static String value(WebRequest request, String name) {
        return request.getCookie(name).map(HttpCookie::getValue).orElse("");
    }
    private static String cookie(String name, String value, long seconds) {
        var path = LOGIN.equals(name) ? "/app/callback" : "/shop-account";
        return name + "=" + value + "; Path=" + path + "; HttpOnly; SameSite=Lax; Max-Age=" + seconds
                + (getProperty("fluxzero.auth.external-base-url", "").startsWith("https:") ? "; Secure" : "");
    }
    static WebResponse.Builder noStore() { return WebResponse.builder().header("Cache-Control", "no-store"); }
    static WebResponse denied() { return noStore().status(401).payload("Sign in again to continue").build(); }
    private static WebResponse.Builder redirect(String url) {
        return noStore().status(303).header("Referrer-Policy", "no-referrer").header("Location", url);
    }
}
