package com.example.ccbilling.web;

import com.example.ccbilling.domain.ActorType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.dto.AuthDtos.AuthUserResponse;
import com.example.ccbilling.dto.AuthDtos.CsrfResponse;
import com.example.ccbilling.dto.AuthDtos.LoginRequest;
import com.example.ccbilling.dto.AuthDtos.RegisterRequest;
import com.example.ccbilling.dto.CustomerDtos.CustomerResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.security.AppPrincipal;
import com.example.ccbilling.security.AppUserDetailsService;
import com.example.ccbilling.service.AuditService;
import com.example.ccbilling.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registration, session login, CSRF token and "who am I". Logout is POST /api/auth/logout (Spring Security). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CustomerService customerService;
    private final AuditService audit;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CustomerService customerService, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.customerService = customerService;
        this.audit = audit;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterRequest request) {
        return customerService.register(request);
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        ActorType type = ActorType.valueOf(request.userType().name());
        String identifier = request.identifier().trim();
        if (type == ActorType.CUSTOMER) {
            identifier = identifier.toLowerCase(Locale.ROOT);
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
                    .unauthenticated(AppUserDetailsService.qualify(type, identifier), request.password()));
        } catch (AuthenticationException ex) {
            // The attempted identifier is not logged (users sometimes type a password into the wrong field).
            audit.recordAs(ActorType.SYSTEM, null, null, AuditAction.LOGIN_FAILED, "Session", null, "userType=" + type);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
        }
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId(); // session fixation protection
        }
        SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppPrincipal principal = (AppPrincipal) authentication.getPrincipal();
        audit.recordAs(principal.getUserType(), principal.getId(), principal.getLoginName(),
                AuditAction.LOGIN_SUCCEEDED, "Session", null, "login");
        return AuthUserResponse.from(principal);
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        return AuthUserResponse.from(principal);
    }
}
