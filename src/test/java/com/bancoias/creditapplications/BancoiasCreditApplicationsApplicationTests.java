package com.bancoias.creditapplications;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "bancoias.messaging.enabled=false")
class BancoiasCreditApplicationsApplicationTests {

    @Test
    void contextLoads() {
    }

}
