package org.cmb.application.service;

import org.cmb.application.domain.RequestIdentity;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {

    RequestIdentity currentIdentity(HttpServletRequest request);
}
