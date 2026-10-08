package dev.kumarnenavath.billing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BillingApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long openAccount() throws Exception {
        String body = mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountName\":\"Acme Warehousing LLC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void billPolicy(long accountId, String plan) throws Exception {
        mvc.perform(post("/api/accounts/{id}/policies", accountId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyNumber\":\"POL-1001\",\"premiumCents\":1200000,\"plan\":\"" + plan + "\",\"effectiveDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());
    }

    private void pay(long accountId, long cents) throws Exception {
        mvc.perform(post("/api/accounts/{id}/payments", accountId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":" + cents + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void quarterlyPlanCreatesFourInvoices() throws Exception {
        long id = openAccount();
        billPolicy(id, "QUARTERLY");
        mvc.perform(get("/api/accounts/{id}/invoices", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].premiumCents").value(300_000))
                .andExpect(jsonPath("$[1].dueDate").value("2026-04-01"))
                .andExpect(jsonPath("$[1].feeCents").value(500));
    }

    @Test
    void paymentsApplyOldestFirstAndOverpaymentBecomesCredit() throws Exception {
        long id = openAccount();
        billPolicy(id, "QUARTERLY"); // 300,000 then 3 x (300,000 + 500 fee)
        pay(id, 450_000);
        mvc.perform(get("/api/accounts/{id}/invoices", id))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[1].status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$[1].outstandingCents").value(150_500));

        pay(id, 2_000_000); // more than everything still owed
        String body = mvc.perform(get("/api/accounts/{id}", id)).andReturn().getResponse().getContentAsString();
        JsonNode account = json.readTree(body);
        long totalOwed = 1_200_000 + 3 * 500;
        org.assertj.core.api.Assertions.assertThat(account.get("creditCents").asLong()).isEqualTo(450_000 + 2_000_000 - totalOwed);
    }

    @Test
    void delinquencyAfterGracePeriodAndClearedByPayment() throws Exception {
        long id = openAccount();
        billPolicy(id, "FULL_PAY");

        mvc.perform(post("/api/delinquency/run").param("asOf", "2026-01-10"))
                .andExpect(jsonPath("$", not(hasItem((int) id)))); // still within the 10 grace days
        mvc.perform(post("/api/delinquency/run").param("asOf", "2026-01-20"))
                .andExpect(jsonPath("$", hasItem((int) id)));
        mvc.perform(get("/api/accounts/{id}", id)).andExpect(jsonPath("$.status").value("DELINQUENT"));

        pay(id, 1_200_000);
        mvc.perform(get("/api/accounts/{id}", id)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void validationAndNotFoundReturnProblemDetails() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"accountName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("accountName")));
        mvc.perform(get("/api/accounts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account 999 not found"));
    }
}
