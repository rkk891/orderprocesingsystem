package com.rkk.orderprocessing.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rkk.orderprocessing.shared.api.OpenApiDocumentController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies that the production profile does not expose local API exploration surfaces. */
@WebMvcTest(OpenApiDocumentController.class)
@ActiveProfiles("prod")
@Import(OpenApiDocumentController.class)
class OpenApiProductionExposureMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void productionProfileDoesNotServeTheOpenApiContract() throws Exception {
        mockMvc.perform(get("/openapi.yaml"))
                .andExpect(status().isNotFound());
    }
}
