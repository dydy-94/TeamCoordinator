package org.cmb.application.domain;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {

    RequestIdentity currentIdentity(HttpServletRequest request);
}
