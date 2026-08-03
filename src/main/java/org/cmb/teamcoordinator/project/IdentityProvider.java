package org.cmb.teamcoordinator.project;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {

    RequestIdentity currentIdentity(HttpServletRequest request);
}
