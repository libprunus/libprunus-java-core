package com.example.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IndexController.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersIndexTemplate() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
    }
}
