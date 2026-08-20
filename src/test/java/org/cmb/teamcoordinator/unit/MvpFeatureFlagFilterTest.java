package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cmb.teamcoordinator.common.MvpFeatureFlagFilter;
import org.cmb.common.config.DigitalTeamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MvpFeatureFlagFilterTest {

    @Test
    void emergencyStopBlocksBusinessApisButLeavesHealthAvailable() throws Exception {
        DigitalTeamProperties properties = new DigitalTeamProperties();
        properties.getRollout().setEmergencyStop(true);
        MvpFeatureFlagFilter filter = new MvpFeatureFlagFilter(properties);

        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/projects/project-any"),
                apiResponse,
                new MockFilterChain());
        assertEquals(503, apiResponse.getStatus());
        assertEquals(
                "{\"code\":\"MVP_DISABLED\","
                        + "\"message\":\"Digital Team is temporarily unavailable\"}",
                apiResponse.getContentAsString());

        MockHttpServletResponse healthResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/health"),
                healthResponse,
                new MockFilterChain());
        assertEquals(200, healthResponse.getStatus());
    }
}
