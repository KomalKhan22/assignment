
package com.example.assignment.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class WalletApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String createWallet(String currency) throws Exception {
        String body = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"CUST-" + UUID.randomUUID()
                                + "\",\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.id");
    }

    private void deposit(String walletId, String amount) throws Exception {
        mockMvc.perform(post("/api/wallets/{id}/deposit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private static String transferJson(String from, String to, String amount) {
        return "{\"sourceWalletId\":\"" + from
                + "\",\"destinationWalletId\":\"" + to
                + "\",\"amount\":" + amount + "}";
    }

    // ------------------------------------------------------------ wallets API

    @Test
    void createAndRetrieveWallet_startsAtZero() throws Exception {
        String id = createWallet("PKR");

        mockMvc.perform(get("/api/wallets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.customerId").exists())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    void getWallet_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/wallets/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ------------------------------------------------------------ transfer API


    @Test
    void transfer_insufficientFunds_returns400_andBalancesAreUntouched() throws Exception {
        String a = createWallet("PKR");
        String b = createWallet("PKR");
        deposit(a, "100.00");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, b, "100.01")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/wallets/{id}", a))
                .andExpect(jsonPath("$.balance").value(100.0));

        mockMvc.perform(get("/api/wallets/{id}", b))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void transfer_invalidAmount_returns400() throws Exception {
        String a = createWallet("PKR");
        String b = createWallet("PKR");
        deposit(a, "100.00");

        for (String amount : new String[]{"0", "-10", "0.001"}) {
            mockMvc.perform(post("/api/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferJson(a, b, amount)))
                    .andExpect(status().isBadRequest());
        }
    }
}
