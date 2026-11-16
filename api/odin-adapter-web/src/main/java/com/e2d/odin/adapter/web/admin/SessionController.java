package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.ActorResolutionFilter;
import com.e2d.odin.adapter.web.security.ClientAddress;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.adapter.web.security.LoginRateLimiter;
import com.e2d.odin.application.identity.DescribeAdmin;
import com.e2d.odin.application.identity.LogInAdmin;
import com.e2d.odin.application.identity.LogOutAdmin;
import com.e2d.odin.application.identity.NewSession;
import com.e2d.odin.application.identity.OwnAdminSessions;
import com.e2d.odin.application.organization.ListCompanies;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.ValidationException;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
public class SessionController {

    private final LogInAdmin logIn;
    private final LogOutAdmin logOut;
    private final LoginRateLimiter limiter;
    private final ListCompanies listCompanies;
    private final DescribeAdmin describeAdmin;
    private final InstallationSettings installation;
    private final ClientAddress addresses;
    private final OwnAdminSessions ownSessions;

    public SessionController(LogInAdmin logIn, LogOutAdmin logOut, LoginRateLimiter limiter,
            ListCompanies listCompanies, DescribeAdmin describeAdmin, InstallationSettings installation,
            ClientAddress addresses, OwnAdminSessions ownSessions) {
        this.ownSessions = ownSessions;
        this.logIn = logIn;
        this.logOut = logOut;
        this.limiter = limiter;
        this.listCompanies = listCompanies;
        this.describeAdmin = describeAdmin;
        this.installation = installation;
        this.addresses = addresses;
    }

    @PostMapping("/session")
    public ResponseEntity<Responses.UserRef> login(@RequestBody Requests.Login request, HttpServletRequest http) {
        if (request.email() == null || request.email().isBlank() || request.password() == null
                || request.password().isEmpty()) {
            throw new ValidationException("Faltan el correo o la contraseña");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String ip = addresses.of(http);
        limiter.check(email, ip);
        NewSession session = logInRecordingFailure(request, email, ip);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(session.token(), false).toString())
                .body(new Responses.UserRef(session.userId().value()));
    }

    private NewSession logInRecordingFailure(Requests.Login request, String email, String ip) {
        try {
            return logIn.execute(request.email(), request.password());
        } catch (UnauthorizedException e) {
            limiter.recordFailure(email, ip);
            throw e;
        }
    }

    @DeleteMapping("/session")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> logout(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @CookieValue(name = ActorResolutionFilter.SESSION_COOKIE, required = false) String token) {
        logOut.execute(actor, token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", true).toString()).build();
    }

    @GetMapping("/me")
    public Responses.AdminMe me(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @CookieValue(name = ActorResolutionFilter.SESSION_COOKIE, required = false) String token) {
        return Responses.AdminMe.from(actor, describeAdmin.execute(actor), listCompanies.execute(actor),
                installation.scannedDocumentsSupported(), ownSessions.expiry(token).orElse(null));
    }

    @GetMapping("/sessions")
    public List<Responses.AdminSessionView> sessions(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @CookieValue(name = ActorResolutionFilter.SESSION_COOKIE, required = false) String token) {
        return ownSessions.list(actor, token).stream().map(Responses.AdminSessionView::from).toList();
    }

    @DeleteMapping("/sessions/{id}")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> closeSession(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID id) {
        ownSessions.close(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseCookie cookie(String value, boolean expire) {
        return ResponseCookie.from(ActorResolutionFilter.SESSION_COOKIE, value).httpOnly(true).sameSite("Strict")
                .path("/").maxAge(expire ? 0 : -1).build();
    }
}
