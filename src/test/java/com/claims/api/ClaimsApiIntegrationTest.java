package com.claims.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end through HTTP, security, Flyway migrations and JPA, against in-memory H2.
 * "Today" is pinned to 2026-10-15 so the seeded 2026 policies are in force.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClaimsApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    com.claims.api.claim.ClaimRepository claimRepository;

    @Autowired
    com.claims.api.policy.PolicyRepository policyRepository;

    @Autowired
    com.claims.api.audit.AuditEventRepository auditEventRepository;

    @org.junit.jupiter.api.BeforeEach
    void resetDbState() {
        claimRepository.deleteAll();
        auditEventRepository.deleteAll();
        policyRepository.findByPolicyNumber("POL-1001").ifPresent(p -> {
            org.springframework.test.util.ReflectionTestUtils.setField(p, "deductibleMet", new java.math.BigDecimal("0.00"));
            org.springframework.test.util.ReflectionTestUtils.setField(p, "benefitsPaidYtd", new java.math.BigDecimal("0.00"));
            policyRepository.save(p);
        });
        policyRepository.findByPolicyNumber("POL-1002").ifPresent(p -> {
            org.springframework.test.util.ReflectionTestUtils.setField(p, "deductibleMet", new java.math.BigDecimal("500.00"));
            org.springframework.test.util.ReflectionTestUtils.setField(p, "benefitsPaidYtd", new java.math.BigDecimal("4900.00"));
            policyRepository.save(p);
        });
    }

    // ------------------------------------------------------------- helpers

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + extract(body, "accessToken");
    }

    private long submit(String token, String policy, String code, String serviceDate, String amount) throws Exception {
        String body = mvc.perform(post("/api/v1/claims")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(policy, code, serviceDate, amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(extract(body, "id"));
    }

    private static String claimJson(String policy, String code, String serviceDate, String amount) {
        return """
                {"policyNumber":"%s","providerName":"Riverside Clinic","serviceCode":"%s",
                 "serviceDate":"%s","billedAmount":%s}
                """.formatted(policy, code, serviceDate, amount);
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"?([^\",}]+)").matcher(json);
        if (!m.find()) {
            throw new AssertionError(field + " not found in " + json);
        }
        return m.group(1);
    }

    // --------------------------------------------------------------- tests

    @Test
    @DisplayName("happy path: submit -> review -> adjudicate (approved with benefit) -> pay")
    void fullLifecycle() throws Exception {
        String member = login("member1");
        String adjuster = login("adjuster1");

        long id = submit(member, "POL-1001", "99213", "2026-10-01", "1000.00");

        mvc.perform(post("/api/v1/adjuster/claims/{id}/review", id).header("Authorization", adjuster))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // $500 deductible, then 80% of $500 = $400 plan, member $600
        mvc.perform(post("/api/v1/adjuster/claims/{id}/adjudicate", id).header("Authorization", adjuster))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("APPROVED"))
                .andExpect(jsonPath("$.claim.benefit.deductibleApplied").value(500.00))
                .andExpect(jsonPath("$.claim.benefit.planPaid").value(400.00))
                .andExpect(jsonPath("$.claim.benefit.memberResponsibility").value(600.00))
                .andExpect(jsonPath("$.ruleResults", hasSize(4)));

        mvc.perform(post("/api/v1/adjuster/claims/{id}/pay", id).header("Authorization", adjuster))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.allowedNextStatuses", hasSize(0)));

        // Member sees the final result
        mvc.perform(get("/api/v1/claims/{id}", id).header("Authorization", member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // Accumulators were updated: second claim skips the deductible entirely
        long second = submit(member, "POL-1001", "99214", "2026-10-02", "100.00");
        mvc.perform(post("/api/v1/adjuster/claims/{id}/review", second).header("Authorization", adjuster));
        mvc.perform(post("/api/v1/adjuster/claims/{id}/adjudicate", second).header("Authorization", adjuster))
                .andExpect(jsonPath("$.claim.benefit.deductibleApplied").value(0.00))
                .andExpect(jsonPath("$.claim.benefit.planPaid").value(80.00));
    }

    @Test
    @DisplayName("annual maximum: plan pays only the $100 left on POL-1002")
    void annualMaximumCap() throws Exception {
        String adjuster = login("adjuster1");
        long id = submit(login("member2"), "POL-1002", "80053", "2026-09-30", "1000.00");

        mvc.perform(post("/api/v1/adjuster/claims/{id}/review", id).header("Authorization", adjuster));
        mvc.perform(post("/api/v1/adjuster/claims/{id}/adjudicate", id).header("Authorization", adjuster))
                .andExpect(jsonPath("$.claim.status").value("APPROVED"))
                .andExpect(jsonPath("$.claim.benefit.planPaid").value(100.00))
                .andExpect(jsonPath("$.claim.benefit.memberResponsibility").value(900.00));
    }

    @Test
    @DisplayName("rules: a late, uncovered claim is denied with every reason listed")
    void deniedWithAllReasons() throws Exception {
        String adjuster = login("adjuster1");
        // 2026-07-01 + 90 days = 2026-09-29, today is 2026-10-15 -> late; 15780 is not covered
        long id = submit(login("member1"), "POL-1001", "15780", "2026-07-01", "250.00");

        mvc.perform(post("/api/v1/adjuster/claims/{id}/review", id).header("Authorization", adjuster));
        mvc.perform(post("/api/v1/adjuster/claims/{id}/adjudicate", id).header("Authorization", adjuster))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("DENIED"))
                .andExpect(jsonPath("$.claim.denialReason", containsString("COVERAGE")))
                .andExpect(jsonPath("$.claim.denialReason", containsString("TIMELY_FILING")))
                .andExpect(jsonPath("$.claim.benefit").doesNotExist());
    }

    @Test
    @DisplayName("rules: the second identical claim is denied as a duplicate")
    void duplicateIsDenied() throws Exception {
        String member = login("member1");
        String adjuster = login("adjuster1");
        long first = submit(member, "POL-1001", "99213", "2026-10-01", "120.00");
        long second = submit(member, "POL-1001", "99213", "2026-10-01", "120.00");

        mvc.perform(post("/api/v1/adjuster/claims/{id}/review", second).header("Authorization", adjuster));
        mvc.perform(post("/api/v1/adjuster/claims/{id}/adjudicate", second).header("Authorization", adjuster))
                .andExpect(jsonPath("$.claim.status").value("DENIED"))
                .andExpect(jsonPath("$.claim.denialReason", containsString("DUPLICATE_CLAIM")));

        // The original is unaffected and can still be approved
        mvc.perform(post("/api/v1/adjuster/claims/{id}/review", first).header("Authorization", adjuster));
        mvc.perform(post("/api/v1/adjuster/claims/{id}/adjudicate", first).header("Authorization", adjuster))
                .andExpect(jsonPath("$.claim.status").value("APPROVED"));
    }

    @Test
    @DisplayName("lifecycle: paying a claim that was never reviewed returns 409 with a clear message")
    void invalidTransitionIs409() throws Exception {
        String adjuster = login("adjuster1");
        long id = submit(login("member1"), "POL-1001", "99213", "2026-10-01", "50.00");

        mvc.perform(post("/api/v1/adjuster/claims/{id}/pay", id).header("Authorization", adjuster))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid status transition"))
                .andExpect(jsonPath("$.currentStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.detail", containsString("SUBMITTED to PAID")));
    }

    @Test
    @DisplayName("validation: bad input returns 400 listing each invalid field")
    void validationErrors() throws Exception {
        mvc.perform(post("/api/v1/claims")
                        .header("Authorization", login("member1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("", "ABC", "2099-01-01", "-5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.policyNumber").exists())
                .andExpect(jsonPath("$.errors.serviceCode").value("serviceCode must be a 5-digit code"))
                .andExpect(jsonPath("$.errors.serviceDate").value("serviceDate cannot be in the future"))
                .andExpect(jsonPath("$.errors.billedAmount").exists());
    }

    @Test
    @DisplayName("validation: malformed JSON returns 400, not 500")
    void malformedJson() throws Exception {
        mvc.perform(post("/api/v1/claims")
                        .header("Authorization", login("member1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    @DisplayName("security: no token -> 401, wrong role -> 403, bad password -> 401")
    void security() throws Exception {
        mvc.perform(get("/api/v1/claims"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/adjuster/claims").header("Authorization", login("member1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(post("/api/v1/claims").header("Authorization", login("adjuster1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("POL-1001", "99213", "2026-10-01", "10")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member1\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        mvc.perform(get("/api/v1/claims").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("isolation: a member cannot see or file against another member's claims/policies")
    void memberIsolation() throws Exception {
        String member1 = login("member1");
        String member2 = login("member2");
        long id = submit(member1, "POL-1001", "99213", "2026-10-01", "75.00");

        mvc.perform(get("/api/v1/claims/{id}", id).header("Authorization", member2))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/claims").header("Authorization", member2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("POL-1001", "99213", "2026-10-01", "10")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/claims").header("Authorization", member2))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("adjuster queue can be filtered by status and paginated; bad status value is a 400")
    void adjusterQueue() throws Exception {
        String adjuster = login("adjuster1");
        String member = login("member1");
        submit(member, "POL-1001", "99213", "2026-10-01", "75.00");
        submit(member, "POL-1001", "99214", "2026-10-02", "85.00");

        mvc.perform(get("/api/v1/adjuster/claims")
                        .param("status", "SUBMITTED")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", adjuster))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mvc.perform(get("/api/v1/adjuster/claims").param("status", "BOGUS").header("Authorization", adjuster))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("BOGUS")));
    }

    @Test
    @DisplayName("Swagger UI, OpenAPI docs and Actuator health are public; metrics require ADMIN")
    void swaggerAndActuator() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.info.title").value("Insurance Claims Processing API"));

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/metrics").header("Authorization", login("member1")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/actuator/metrics").header("Authorization", login("admin1")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("self-registration -> admin issues policy -> member views policy and submits claim -> audit trail records all")
    void registrationAdminPolicyAndAuditFlow() throws Exception {
        // 1. Public member registration
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newmember","password":"correct-horse-battery-staple","fullName":"New Member"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newmember"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.enabled").value(true));

        // 2. Duplicate registration returns 409
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newmember","password":"correct-horse-battery-staple","fullName":"Dup"}
                                """))
                .andExpect(status().isConflict());

        // 3. Admin creates policy for the new member and adds a covered service
        String admin = login("admin1");
        mvc.perform(post("/api/v1/admin/policies")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNumber":"POL-9001","memberUsername":"newmember",
                                 "startDate":"2026-01-01","endDate":"2026-12-31",
                                 "annualDeductible":250.00,"coinsuranceRate":0.80,"annualMaximum":5000.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value("POL-9001"))
                .andExpect(jsonPath("$.remainingDeductible").value(250.00));

        mvc.perform(put("/api/v1/admin/covered-services/99215")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Complex office visit\",\"covered\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceCode").value("99215"))
                .andExpect(jsonPath("$.covered").value(true));

        // 4. New member logs in, views own policy, and submits a claim
        String loginBody = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newmember\",\"password\":\"correct-horse-battery-staple\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newMemberToken = "Bearer " + extract(loginBody, "accessToken");

        mvc.perform(get("/api/v1/me/policies").header("Authorization", newMemberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].policyNumber").value("POL-9001"));

        long claimId = submit(newMemberToken, "POL-9001", "99215", "2026-10-10", "200.00");

        // 5. Audit log contains the claim submission with correlation ID
        mvc.perform(get("/api/v1/admin/audit-events")
                        .param("entityType", "CLAIM")
                        .param("entityId", String.valueOf(claimId))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action").value("CLAIM_SUBMITTED"))
                .andExpect(jsonPath("$.content[0].actor").value("newmember"))
                .andExpect(jsonPath("$.content[0].requestId", notNullValue()));
    }

    @Test
    @DisplayName("admin disabling a user revokes their existing JWT immediately")
    void disablingUserRevokesTokenImmediately() throws Exception {
        String admin = login("admin1");

        String userBody = mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"tempadj","password":"temporary-adjuster-pw","fullName":"Temp Adj","role":"ADJUSTER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = Long.parseLong(extract(userBody, "id"));

        String loginBody = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tempadj\",\"password\":\"temporary-adjuster-pw\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tempToken = "Bearer " + extract(loginBody, "accessToken");

        // Works before disabling
        mvc.perform(get("/api/v1/adjuster/claims").header("Authorization", tempToken))
                .andExpect(status().isOk());

        // Disable user
        mvc.perform(patch("/api/v1/admin/users/{id}/enabled", userId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Existing token is immediately rejected
        mvc.perform(get("/api/v1/adjuster/claims").header("Authorization", tempToken))
                .andExpect(status().isUnauthorized());
    }
}
