package org.cmb.application.service.impl;
import org.cmb.application.service.IdentityProvider;
import org.cmb.application.domain.RequestIdentity;

import javax.servlet.http.HttpServletRequest;
import org.cmb.common.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class HeaderIdentityProvider implements IdentityProvider {

    @Override
    public RequestIdentity currentIdentity(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        String userId = request.getHeader("X-User-Id");
        if (isBlank(tenantId) || isBlank(userId)) {
            throw ApiException.unauthorized("IDENTITY_REQUIRED", "X-Tenant-Id and X-User-Id are required.");
        }
        return new RequestIdentity(tenantId.trim(), userId.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
