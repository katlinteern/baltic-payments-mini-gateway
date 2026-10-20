package com.baltic.gateway.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

final class PaymentFlow {
    static final String ALICE_IBAN = "LT121000011101001000";
    static final String BOB_IBAN = "EE382200221020145685";

    private final MockMvc mvc;
    private final ObjectMapper mapper;

    PaymentFlow(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    static String body(String from, String to, String name, String amount, String reference) {
        return """
                {"fromIban":"%s","toIban":"%s","beneficiaryName":"%s","amount":%s,"currency":"EUR","reference":"%s"}
                """.formatted(from, to, name, amount, reference);
    }

    static String aliceToBob(String amount, String reference) {
        return body(ALICE_IBAN, BOB_IBAN, "Bob Tamm", amount, reference);
    }

    ResultActions create(String token, String body) throws Exception {
        return mvc.perform(post("/api/payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    ResultActions confirm(String token, String id, boolean acknowledgeNameMismatch) throws Exception {
        return mvc.perform(post("/api/payments/{id}/confirmation", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledgeNameMismatch\":" + acknowledgeNameMismatch + "}"));
    }

    ResultActions poll(String token, String id) throws Exception {
        return mvc.perform(get("/api/payments/{id}", id).header("Authorization", "Bearer " + token));
    }

    ResultActions cancel(String token, String id) throws Exception {
        return mvc.perform(delete("/api/payments/{id}", id).header("Authorization", "Bearer " + token));
    }

    String createdId(String token, String body) throws Exception {
        return json(create(token, body).andExpect(status().isCreated())).get("id").asText();
    }

    JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    JsonNode pay(String token, String body) throws Exception {
        return pay(token, body, false);
    }

    JsonNode pay(String token, String body, boolean acknowledgeNameMismatch) throws Exception {
        String id = createdId(token, body);
        confirm(token, id, acknowledgeNameMismatch).andExpect(status().isOk());
        JsonNode payment = json(poll(token, id).andExpect(status().isOk()));
        if (!"COMPLETED".equals(payment.get("status").asText())) {
            throw new AssertionError("Payment did not complete: " + payment);
        }
        return payment;
    }
}
