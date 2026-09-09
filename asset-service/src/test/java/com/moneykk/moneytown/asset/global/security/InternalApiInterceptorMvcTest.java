package com.moneykk.moneytown.asset.global.security;

import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalApiInterceptorMvcTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new TestInternalController())
                .addInterceptors(new InternalApiInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("SYSTEM 권한은 내부 API를 호출할 수 있다")
    void allowsSystemRole() throws Exception {
        mvc.perform(get("/api/v1/internal/test")
                        .header("X-User-Role", "SYSTEM"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SYSTEM 외 권한은 내부 API를 호출할 수 없다")
    void rejectsNonSystemRole() throws Exception {
        mvc.perform(get("/api/v1/internal/test")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ASSET_403_12"));
    }

    @RestController
    private static class TestInternalController {

        @GetMapping("/api/v1/internal/test")
        String get() {
            return "ok";
        }
    }
}
