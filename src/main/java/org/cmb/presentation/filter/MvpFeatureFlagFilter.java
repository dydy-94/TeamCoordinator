package org.cmb.presentation.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.cmb.common.config.DigitalTeamProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MvpFeatureFlagFilter extends OncePerRequestFilter {

    private final DigitalTeamProperties properties;

    public MvpFeatureFlagFilter(DigitalTeamProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        DigitalTeamProperties.Rollout rollout = properties.getRollout();
        if (rollout.isEnabled() && !rollout.isEmergencyStop()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"MVP_DISABLED\",\"message\":\"Digital Team is temporarily unavailable\"}");
    }
}
