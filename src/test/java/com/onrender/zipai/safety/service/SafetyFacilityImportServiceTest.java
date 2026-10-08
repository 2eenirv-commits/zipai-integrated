package com.onrender.zipai.safety.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

class SafetyFacilityImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void importsWithJdbcBatchesAndKeepsIdempotentUpsertSql() throws Exception {
        Path csv = tempDir.resolve("facilities.csv");
        Files.write(
            csv,
            List.of(
                "source_id,name,facility_type,address,latitude,longitude,purpose,camera_count,source,source_updated_at",
                "source-1,light-1,STREET_LIGHT,address-1,37.1,127.1,,1,test,2026-10-08",
                "source-2,light-2,STREET_LIGHT,address-2,37.2,127.2,,1,test,2026-10-08",
                "source-3,light-3,STREET_LIGHT,address-3,37.3,127.3,,1,test,2026-10-08"
            ),
            StandardCharsets.UTF_8
        );

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Integer> executedBatchSizes = new ArrayList<>();
        doAnswer(invocation -> {
            Collection<?> rows = invocation.getArgument(1);
            executedBatchSizes.add(rows.size());
            return new int[][] {new int[rows.size()]};
        }).when(jdbc).batchUpdate(
            anyString(),
            anyCollection(),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class)
        );
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        SafetyFacilityImportService service = new SafetyFacilityImportService(
            jdbc,
            2,
            tempDir.resolve("rejected.csv")
        );
        SafetyFacilityImportService.ImportResult result = service.importCsv(csv);

        assertEquals(List.of(2, 1), executedBatchSizes);
        assertEquals(3, result.succeededRows());
        assertEquals(0, result.rejectedRows());
        assertEquals(1000, SafetyFacilityImportService.DEFAULT_BATCH_SIZE);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).batchUpdate(
            sqlCaptor.capture(),
            anyCollection(),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class)
        );
        for (String sql : sqlCaptor.getAllValues()) {
            assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
            assertTrue(sql.contains("latitude = COALESCE(VALUES(latitude), latitude)"));
            assertTrue(sql.contains("longitude = COALESCE(VALUES(longitude), longitude)"));
        }
    }
}
