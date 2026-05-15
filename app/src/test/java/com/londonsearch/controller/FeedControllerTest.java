package com.londonsearch.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void feedPageLoads() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed"))
                .andExpect(model().attributeExists("properties", "areas", "selectedArea", "selectedFilter"));
    }

    @Test
    @WithMockUser
    void feedFiltersByArea() throws Exception {
        mockMvc.perform(get("/").param("area", "Mayfair"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedArea", "Mayfair"));
    }

    @Test
    @WithMockUser
    void feedFiltersByStatus() throws Exception {
        mockMvc.perform(get("/").param("filter", "new"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedFilter", "new"));
    }
}
