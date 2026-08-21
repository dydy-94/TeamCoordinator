package org.cmb.application.service;

import org.cmb.application.domain.RequestIdentity;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {

    RequestIdentity currentIdentity(HttpServletRequest request);

    /**
     * 仅取 X-User-Id(不校验租户),供租户列表/租户管理这类
     * 「用户可能携带过期租户头」的端点使用。
     */
    default String currentUserId(HttpServletRequest request) {
        return null;
    }
}
