package com.onrender.zipai.safety.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ImportSafetyFacilitiesTest {

    @Test
    void enablesBatchRewriteWhenJdbcUrlHasNoQuery() {
        assertEquals(
            "jdbc:mysql://localhost:3306/zipai?rewriteBatchedStatements=true",
            ImportSafetyFacilities.withBatchRewrite("jdbc:mysql://localhost:3306/zipai")
        );
    }

    @Test
    void enablesBatchRewriteAlongsideExistingOptions() {
        assertEquals(
            "jdbc:mysql://localhost:3306/zipai?useSSL=false&rewriteBatchedStatements=true",
            ImportSafetyFacilities.withBatchRewrite(
                "jdbc:mysql://localhost:3306/zipai?useSSL=false"
            )
        );
    }

    @Test
    void preservesExplicitBatchRewriteSetting() {
        String url = "jdbc:mysql://localhost:3306/zipai?rewriteBatchedStatements=false";
        assertEquals(url, ImportSafetyFacilities.withBatchRewrite(url));
    }
}
