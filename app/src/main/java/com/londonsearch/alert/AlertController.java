package com.londonsearch.alert;

import com.londonsearch.model.AlertRecord;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

@Controller
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final SmartLinkService smartLinkService;

    public AlertController(SmartLinkService smartLinkService) {
        this.smartLinkService = smartLinkService;
    }

    @GetMapping("/alert/{token}")
    public String handleSmartLink(@PathVariable String token, HttpSession session) {
        Optional<AlertRecord> alertRecord = smartLinkService.validateToken(token);

        if (alertRecord.isEmpty()) {
            log.warn("Invalid or expired smart link token: {}", token);
            return "redirect:/login?expired";
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        log.info("Smart link token {} authenticated successfully, redirecting to feed", token);
        return "redirect:/?filter=new";
    }
}
