package com.onrender.zipai.service;

import com.onrender.zipai.domain.ZipaiUser;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PropertyListingService {
    private final JdbcTemplate jdbc;
    private final PropertyListingImageStorageService images;

    public PropertyListingService(JdbcTemplate jdbc, PropertyListingImageStorageService images) {
        this.jdbc = jdbc;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> find(String dealType, List<Long> ids, boolean includeStudy) {
        StringBuilder sql = new StringBuilder("SELECT * FROM property_listing WHERE status='active'");
        List<Object> args = new ArrayList<>();
        if (!includeStudy) sql.append(" AND study_data=FALSE");
        if (dealType != null && !dealType.isBlank()) {
            sql.append(" AND deal_type=?");
            args.add(dealType.trim().toUpperCase());
        }
        if (ids != null && !ids.isEmpty()) {
            sql.append(" AND property_id IN (").append("?,".repeat(ids.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            args.addAll(ids);
        }
        sql.append(" ORDER BY updated_at DESC, property_id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, rowNum) -> toMap(rs.getLong("property_id"), rs.getString("deal_type"), rs.getString("building_type"),
                rs.getString("title"), rs.getString("address"), rs.getString("sido"), rs.getString("sigungu"), rs.getString("neighborhood"),
                decimal(rs.getBigDecimal("latitude")), decimal(rs.getBigDecimal("longitude")), nullableLong(rs, "sale_price"), rs.getLong("deposit"),
                rs.getLong("monthly_rent"), rs.getLong("maintenance_fee"), decimal(rs.getBigDecimal("area")), rs.getString("floor_text"),
                rs.getBoolean("parking"), rs.getBoolean("elevator"), rs.getBoolean("pet"), rs.getString("description"), rs.getString("contact"),
                rs.getString("source_type"), rs.getString("source_name"), rs.getBoolean("study_data"), rs.getString("source_url"),
                rs.getTimestamp("last_seen_at"), rs.getTimestamp("updated_at")), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findMine(Long userId) {
        return jdbc.query("SELECT * FROM property_listing WHERE owner_user_id=? AND source_type='USER' ORDER BY created_at DESC, property_id DESC",
                (rs, rowNum) -> {
                    Map<String, Object> item = toMap(rs.getLong("property_id"), rs.getString("deal_type"), rs.getString("building_type"),
                            rs.getString("title"), rs.getString("address"), rs.getString("sido"), rs.getString("sigungu"), rs.getString("neighborhood"),
                            decimal(rs.getBigDecimal("latitude")), decimal(rs.getBigDecimal("longitude")), nullableLong(rs, "sale_price"), rs.getLong("deposit"),
                            rs.getLong("monthly_rent"), rs.getLong("maintenance_fee"), decimal(rs.getBigDecimal("area")), rs.getString("floor_text"),
                            rs.getBoolean("parking"), rs.getBoolean("elevator"), rs.getBoolean("pet"), rs.getString("description"), rs.getString("contact"),
                            rs.getString("source_type"), rs.getString("source_name"), rs.getBoolean("study_data"), rs.getString("source_url"),
                            rs.getTimestamp("last_seen_at"), rs.getTimestamp("updated_at"));
                    item.put("status", rs.getString("status"));
                    item.put("ownerContact", rs.getString("contact"));
                    return item;
                }, userId);
    }

    @Transactional
    public Map<String, Object> updateMyListing(Long userId, long propertyId, Map<String, String> fields, MultipartFile[] files) {
        requireOwnedListing(userId, propertyId);
        require(fields, "title", "address", "sigungu", "buildingType", "dealType", "area", "floor", "contact");
        String dealType = value(fields, "dealType").toUpperCase();
        if (!List.of("SALE", "JEONSE", "MONTHLY").contains(dealType)) throw new IllegalArgumentException("거래 유형을 확인해 주세요.");
        if ("SALE".equals(dealType)) require(fields, "salePrice"); else require(fields, "deposit");
        if ("MONTHLY".equals(dealType)) require(fields, "monthly");
        Long salePrice = "SALE".equals(dealType) ? longNumber(fields, "salePrice") : null;
        long deposit = "SALE".equals(dealType) ? 0L : longNumber(fields, "deposit");
        long monthly = "MONTHLY".equals(dealType) ? longNumber(fields, "monthly") : 0L;
        jdbc.update("""
                UPDATE property_listing SET deal_type=?,building_type=?,title=?,address=?,sido=?,sigungu=?,neighborhood=?,
                latitude=?,longitude=?,sale_price=?,deposit=?,monthly_rent=?,maintenance_fee=?,area=?,floor_text=?,
                parking=?,elevator=?,pet=?,description=?,contact=?,updated_at=?
                WHERE property_id=? AND owner_user_id=? AND source_type='USER'
                """, dealType, fields.get("buildingType"), fields.get("title").trim(), fields.get("address").trim(), value(fields, "sido"),
                fields.get("sigungu"), value(fields, "neighborhood"), number(fields, "lat"), number(fields, "lng"), salePrice, deposit, monthly,
                validMaintenance(longNumberDefault(fields, "maintenance", 0L)), number(fields, "area"), fields.get("floor"), bool(fields, "parking"),
                bool(fields, "elevator"), bool(fields, "pet"), value(fields, "description"), fields.get("contact"),
                Timestamp.valueOf(LocalDateTime.now()), propertyId, userId);
        List<MultipartFile> uploadFiles = nonEmpty(files);
        if (uploadFiles.size() > 10) throw new IllegalArgumentException("매물 사진은 최대 10장까지 등록할 수 있습니다.");
        if (!uploadFiles.isEmpty()) {
            List<String> oldNames = storedImageNames(propertyId);
            jdbc.update("DELETE FROM property_listing_image WHERE property_id=?", propertyId);
            int representative = intNumberDefault(fields, "representativeIndex", 0);
            if (representative < 0 || representative >= uploadFiles.size()) representative = 0;
            saveImages(propertyId, uploadFiles, representative);
            oldNames.forEach(images::delete);
        }
        return findMine(userId).stream().filter(item -> Number.class.cast(item.get("id")).longValue() == propertyId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("매물을 찾을 수 없습니다."));
    }

    @Transactional
    public void deleteMyListing(Long userId, long propertyId) {
        requireOwnedListing(userId, propertyId);
        List<String> storedNames = storedImageNames(propertyId);
        jdbc.update("DELETE FROM property_favorite WHERE property_id=?", propertyId);
        jdbc.update("DELETE FROM property_listing_image WHERE property_id=?", propertyId);
        int deleted = jdbc.update("DELETE FROM property_listing WHERE property_id=? AND owner_user_id=? AND source_type='USER'", propertyId, userId);
        if (deleted == 0) throw new IllegalArgumentException("삭제할 등록 매물을 찾지 못했습니다.");
        storedNames.forEach(images::delete);
    }

    @Transactional
    public Map<String, Object> changeMyStatus(Long userId, long propertyId, String requestedStatus) {
        String status = requestedStatus == null ? "" : requestedStatus.trim().toLowerCase();
        if (!List.of("active", "closed").contains(status)) {
            throw new IllegalArgumentException("매물 상태를 확인해 주세요.");
        }
        int updated = jdbc.update("UPDATE property_listing SET status=?, updated_at=? WHERE property_id=? AND owner_user_id=? AND source_type='USER'",
                status, Timestamp.valueOf(LocalDateTime.now()), propertyId, userId);
        if (updated == 0) throw new IllegalArgumentException("상태를 변경할 수 있는 등록 매물을 찾지 못했습니다.");
        return findMine(userId).stream().filter(item -> Number.class.cast(item.get("id")).longValue() == propertyId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("매물을 찾을 수 없습니다."));
    }

    @Transactional
    public Map<String, Object> createListing(ZipaiUser user, Map<String, String> fields, MultipartFile[] files) {
        require(fields, "title", "address", "sigungu", "buildingType", "dealType", "area", "floor", "contact");
        String dealType = value(fields, "dealType").toUpperCase();
        if (!List.of("SALE", "JEONSE", "MONTHLY").contains(dealType)) {
            throw new IllegalArgumentException("거래 유형을 확인해 주세요.");
        }
        if ("SALE".equals(dealType)) require(fields, "salePrice");
        else require(fields, "deposit");
        if ("MONTHLY".equals(dealType)) require(fields, "monthly");
        List<MultipartFile> uploadFiles = nonEmpty(files);
        if (uploadFiles.isEmpty()) throw new IllegalArgumentException("매물 사진을 1장 이상 첨부해 주세요.");
        if (uploadFiles.size() > 10) throw new IllegalArgumentException("매물 사진은 최대 10장까지 등록할 수 있습니다.");

        Long salePrice = "SALE".equals(dealType) ? longNumber(fields, "salePrice") : null;
        long deposit = "SALE".equals(dealType) ? 0L : longNumber(fields, "deposit");
        long monthly = "MONTHLY".equals(dealType) ? longNumber(fields, "monthly") : 0L;
        long id = insertProperty(user.getId(), "USER", null, null, dealType, fields.get("buildingType"), fields.get("title"), fields.get("address"),
                value(fields, "sido"), fields.get("sigungu"), value(fields, "neighborhood"), number(fields, "lat"), number(fields, "lng"),
                salePrice, deposit, monthly, validMaintenance(longNumberDefault(fields, "maintenance", 0L)), number(fields, "area"), fields.get("floor"),
                bool(fields, "parking"), bool(fields, "elevator"), bool(fields, "pet"), value(fields, "description"), fields.get("contact"), LocalDateTime.now());
        int representative = intNumberDefault(fields, "representativeIndex", 0);
        if (representative < 0 || representative >= uploadFiles.size()) representative = 0;
        saveImages(id, uploadFiles, representative);
        return findById(id);
    }

    @Transactional
    public Map<String, Object> createLegacyRental(ZipaiUser user, Map<String, Object> body) {
        String deal = String.valueOf(body.getOrDefault("deal", "monthly")).equalsIgnoreCase("jeonse") ? "JEONSE" : "MONTHLY";
        String title = text(body, "title");
        String address = text(body, "address");
        String district = text(body, "district");
        String contact = text(body, "contact");
        Double lat = objectNumber(body.get("lat"));
        Double lng = objectNumber(body.get("lng"));
        long deposit = objectLong(body.get("deposit"));
        long monthly = objectLong(body.get("monthly"));
        if (title.isBlank() || address.isBlank()) throw new IllegalArgumentException("매물 제목과 주소가 필요합니다.");
        if (!"경기도".equals(text(body, "sido")) || district.isBlank()) throw new IllegalArgumentException("경기도 시·군 정보를 확인해 주세요.");
        if (lat == null || lng == null || lat < 36.82 || lat > 38.32 || lng < 126.30 || lng > 127.86) throw new IllegalArgumentException("경기도 안의 정확한 주소 좌표가 필요합니다.");
        if (!contact.matches("^01[016789]-?\\d{3,4}-?\\d{4}$")) throw new IllegalArgumentException("연락처 형식을 확인해 주세요.");
        if (deposit < 0 || monthly < 0) throw new IllegalArgumentException("보증금과 월세는 0 이상이어야 합니다.");
        long id = insertProperty(user.getId(), "USER", null, null, deal, text(body, "type"), title, address,
                "경기도", district, text(body, "neighborhood"), lat, lng, null,
                deposit, "JEONSE".equals(deal) ? 0L : monthly, validMaintenance(objectLong(body.get("maintenance"))), objectNumber(body.get("area")),
                text(body, "floor"), boolObject(body.get("parking")), boolObject(body.get("elevator")), boolObject(body.get("pet")), null,
                contact, LocalDateTime.now());
        return findById(id);
    }

    @Transactional
    public Map<String, Integer> importCrawled(String sourceName, String importRunId, boolean studyData, List<Map<String, Object>> items) {
        int inserted = 0, updated = 0, errors = 0;
        LocalDateTime started = LocalDateTime.now();
        sourceName = sourceName == null ? "" : sourceName.trim();
        importRunId = importRunId == null ? "" : importRunId.trim();
        if (sourceName.isBlank() || sourceName.length() > 120) throw new IllegalArgumentException("공급처 이름을 확인해 주세요.");
        if (importRunId.isBlank() || importRunId.length() > 64) throw new IllegalArgumentException("Import 실행 ID를 확인해 주세요.");
        for (Map<String, Object> item : items) {
            try {
                String sourceId = text(item, "sourceId");
                if (sourceId.isBlank()) throw new IllegalArgumentException("sourceId 누락");
                String dealType = blankDefault(text(item, "dealType"), text(item, "deal")).toUpperCase();
                if (dealType.isBlank()) dealType = "SALE";
                if ("매매".equals(dealType)) dealType = "SALE";
                if ("전세".equals(dealType)) dealType = "JEONSE";
                if ("월세".equals(dealType)) dealType = "MONTHLY";
                if ("MONTHLY_RENT".equals(dealType) || "RENT".equals(dealType)) dealType = "MONTHLY";
                if (!List.of("SALE", "JEONSE", "MONTHLY").contains(dealType)) {
                    throw new IllegalArgumentException("지원하지 않는 거래 유형: " + dealType);
                }
                String title = text(item, "title");
                String address = text(item, "address");
                if (title.isBlank() || address.isBlank()) throw new IllegalArgumentException("title/address 누락");
                Long salePrice = "SALE".equals(dealType) ? objectNullableLong(item.get("salePrice")) : null;
                long deposit = "SALE".equals(dealType) ? 0L : objectLong(item.get("deposit"));
                long monthly = "MONTHLY".equals(dealType) ? objectLong(item.get("monthly")) : 0L;
                long maintenance = validMaintenance(objectLong(item.get("maintenance")));
                if ("SALE".equals(dealType) && (salePrice == null || salePrice <= 0)) {
                    throw new IllegalArgumentException("매매가는 0보다 커야 합니다.");
                }
                if (deposit < 0 || monthly < 0 || maintenance < 0) {
                    throw new IllegalArgumentException("가격 정보는 0 이상이어야 합니다.");
                }
                Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM property_listing WHERE source_type='CRAWLING' AND source_id=?", Integer.class, sourceId);
                jdbc.update("""
                    INSERT INTO property_listing (owner_user_id,source_type,source_name,study_data,source_id,import_run_id,source_url,deal_type,building_type,title,address,sido,sigungu,neighborhood,latitude,longitude,sale_price,deposit,monthly_rent,maintenance_fee,area,floor_text,parking,elevator,pet,description,contact,status,source_updated_at,last_seen_at,created_at,updated_at)
                    VALUES (NULL,'CRAWLING',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'active',?,?,?,?)
                    ON DUPLICATE KEY UPDATE source_name=VALUES(source_name),study_data=VALUES(study_data),import_run_id=VALUES(import_run_id),source_url=VALUES(source_url),deal_type=VALUES(deal_type),building_type=VALUES(building_type),title=VALUES(title),address=VALUES(address),sido=VALUES(sido),sigungu=VALUES(sigungu),neighborhood=VALUES(neighborhood),latitude=VALUES(latitude),longitude=VALUES(longitude),sale_price=VALUES(sale_price),deposit=VALUES(deposit),monthly_rent=VALUES(monthly_rent),maintenance_fee=VALUES(maintenance_fee),area=VALUES(area),floor_text=VALUES(floor_text),parking=VALUES(parking),elevator=VALUES(elevator),pet=VALUES(pet),description=VALUES(description),contact=VALUES(contact),source_updated_at=VALUES(source_updated_at),last_seen_at=VALUES(last_seen_at),updated_at=VALUES(updated_at),status='active'
                    """, sourceName, studyData, sourceId, importRunId, text(item,"sourceUrl"), dealType, blankDefault(text(item,"buildingType"), "기타"), title, address,
                    text(item,"sido"), text(item,"sigungu"), text(item,"neighborhood"), objectNumber(item.get("lat")), objectNumber(item.get("lng")),
                    salePrice, deposit, monthly, maintenance, objectNumber(item.get("area")), text(item,"floor"), boolObject(item.get("parking")),
                    boolObject(item.get("elevator")), boolObject(item.get("pet")), text(item,"description"), text(item,"contact"),
                    Timestamp.valueOf(started), Timestamp.valueOf(started), Timestamp.valueOf(started), Timestamp.valueOf(started));
                Long propertyId = jdbc.queryForObject("SELECT property_id FROM property_listing WHERE source_type='CRAWLING' AND source_id=?", Long.class, sourceId);
                String imageUrl = text(item, "imageUrl");
                if (propertyId != null && !imageUrl.isBlank()) {
                    jdbc.update("DELETE FROM property_listing_image WHERE property_id=? AND stored_name IS NULL", propertyId);
                    jdbc.update("INSERT INTO property_listing_image (property_id,image_url,sort_order,representative) VALUES (?,?,0,TRUE)", propertyId, imageUrl);
                }
                if (exists != null && exists > 0) updated++; else inserted++;
            } catch (Exception e) { errors++; }
        }
        jdbc.update("INSERT INTO property_crawl_history (source_name,started_at,finished_at,collected_count,inserted_count,updated_count,error_count,message) VALUES (?,?,?,?,?,?,?,?)",
                sourceName, Timestamp.valueOf(started), Timestamp.valueOf(LocalDateTime.now()), items.size(), inserted, updated, errors, errors == 0 ? "SUCCESS" : "PARTIAL");
        return Map.of("collected", items.size(), "inserted", inserted, "updated", updated, "errors", errors);
    }

    @Transactional
    public int finalizeCrawledSource(String sourceName, String importRunId) {
        if (sourceName == null || sourceName.isBlank() || importRunId == null || importRunId.isBlank()) {
            throw new IllegalArgumentException("공급처 이름과 Import 실행 ID가 필요합니다.");
        }
        return jdbc.update("""
                UPDATE property_listing SET status='closed',updated_at=?
                WHERE source_type='CRAWLING' AND source_name=? AND status='active'
                  AND (import_run_id IS NULL OR import_run_id<>?)
                """, Timestamp.valueOf(LocalDateTime.now()), sourceName.trim(), importRunId.trim());
    }

    @Transactional
    public int deleteStudyListings() {
        jdbc.update("DELETE FROM property_favorite WHERE property_id IN (SELECT property_id FROM property_listing WHERE study_data=TRUE)");
        jdbc.update("DELETE FROM property_listing_image WHERE property_id IN (SELECT property_id FROM property_listing WHERE study_data=TRUE)");
        return jdbc.update("DELETE FROM property_listing WHERE study_data=TRUE");
    }


    @Transactional(readOnly = true)
    public Map<String, Object> findMarketTransactions(String sido, String lawdCd, String dealYmd, String query,
            Long minAmount, Long maxAmount, Double minArea, Double maxArea, Integer minBuildYear,
            String dealingType, Double minLat, Double maxLat, Double minLng, Double maxLng,
            boolean excludeCancelled, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(10, Math.min(requestedSize, 100));
        StringBuilder where = new StringBuilder(" FROM property_market_transaction WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (lawdCd != null && !lawdCd.isBlank()) {
            where.append(" AND lawd_cd=?");
            args.add(lawdCd.trim());
        } else if ("seoul".equalsIgnoreCase(sido)) {
            where.append(" AND lawd_cd LIKE '11%'");
        } else if ("gyeonggi".equalsIgnoreCase(sido)) {
            where.append(" AND lawd_cd LIKE '41%'");
        }
        if (dealYmd != null && !dealYmd.isBlank()) { where.append(" AND deal_ymd=?"); args.add(dealYmd.trim()); }
        if (query != null && !query.isBlank()) {
            String keyword = "%" + query.trim() + "%";
            String compactKeyword = "%" + query.replaceAll("\\s+", "") + "%";
            where.append(" AND (apt_name LIKE ? OR neighborhood LIKE ? OR jibun LIKE ? OR road_name LIKE ?")
                    .append(" OR REPLACE(CONCAT_WS('',apt_name,neighborhood,jibun,road_name),' ','') LIKE ?)");
            args.add(keyword); args.add(keyword); args.add(keyword); args.add(keyword);
            args.add(compactKeyword);
        }
        if (minAmount != null) { where.append(" AND deal_amount>=?"); args.add(minAmount); }
        if (maxAmount != null) { where.append(" AND deal_amount<=?"); args.add(maxAmount); }
        if (minArea != null) { where.append(" AND area>=?"); args.add(minArea); }
        if (maxArea != null) { where.append(" AND area<=?"); args.add(maxArea); }
        if (minBuildYear != null) { where.append(" AND build_year>=?"); args.add(minBuildYear); }
        if (dealingType != null && !dealingType.isBlank()) { where.append(" AND dealing_type=?"); args.add(dealingType.trim()); }
        if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
            where.append(" AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?");
            args.add(minLat); args.add(maxLat); args.add(minLng); args.add(maxLng);
        }
        if (excludeCancelled) { where.append(" AND (cancel_deal_yn IS NULL OR cancel_deal_yn='' OR cancel_deal_yn='N')"); }

        Long count = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        long totalCount = count == null ? 0 : count;
        int totalPages = totalCount == 0 ? 0 : (int) ((totalCount + size - 1) / size);
        if (totalPages > 0 && page >= totalPages) page = totalPages - 1;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        String sql = "SELECT *" + where + " ORDER BY deal_date DESC, transaction_id DESC LIMIT ? OFFSET ?";
        List<Map<String,Object>> items = jdbc.query(sql, (rs, rowNum) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("transactionId", rs.getLong("transaction_id"));
            m.put("sourceId", rs.getString("source_id"));
            m.put("lawdCd", rs.getString("lawd_cd"));
            m.put("dealYmd", rs.getString("deal_ymd"));
            m.put("aptName", rs.getString("apt_name"));
            m.put("neighborhood", rs.getString("neighborhood"));
            m.put("jibun", rs.getString("jibun"));
            m.put("roadName", rs.getString("road_name"));
            m.put("lat", decimal(rs.getBigDecimal("latitude"))); m.put("lng", decimal(rs.getBigDecimal("longitude")));
            m.put("dealAmount", nullableLong(rs, "deal_amount"));
            m.put("area", decimal(rs.getBigDecimal("area")));
            m.put("floor", rs.getString("floor_text"));
            m.put("buildYear", rs.getObject("build_year"));
            java.sql.Date dealDate = rs.getDate("deal_date");
            m.put("dealDate", dealDate == null ? null : dealDate.toLocalDate().toString());
            m.put("dealingType", rs.getString("dealing_type"));
            m.put("cancelDealYn", rs.getString("cancel_deal_yn"));
            return m;
        }, pageArgs.toArray());

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("count", items.size());
        result.put("totalCount", totalCount);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", totalPages);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> findMarketTransactionDetail(long transactionId) {
        List<Map<String,Object>> selected = jdbc.query("SELECT * FROM property_market_transaction WHERE transaction_id=?", (rs, n) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("transactionId", rs.getLong("transaction_id")); m.put("lawdCd", rs.getString("lawd_cd"));
            m.put("dealYmd", rs.getString("deal_ymd")); m.put("aptName", rs.getString("apt_name"));
            m.put("neighborhood", rs.getString("neighborhood")); m.put("jibun", rs.getString("jibun"));
            m.put("roadName", rs.getString("road_name")); m.put("dealAmount", nullableLong(rs, "deal_amount"));
            m.put("area", decimal(rs.getBigDecimal("area"))); m.put("floor", rs.getString("floor_text"));
            m.put("buildYear", rs.getObject("build_year"));
            java.sql.Date date = rs.getDate("deal_date"); m.put("dealDate", date == null ? null : date.toLocalDate().toString());
            m.put("dealingType", rs.getString("dealing_type")); m.put("cancelDealYn", rs.getString("cancel_deal_yn"));
            return m;
        }, transactionId);
        if (selected.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "실거래가를 찾을 수 없습니다.");
        Map<String,Object> item = selected.get(0);
        Double area = item.get("area") instanceof Number number ? number.doubleValue() : null;
        String lawdCd = String.valueOf(item.getOrDefault("lawdCd", ""));
        String neighborhood = String.valueOf(item.getOrDefault("neighborhood", ""));
        List<Map<String,Object>> comparable = jdbc.query("""
                SELECT transaction_id,apt_name,deal_amount,area,floor_text,deal_date
                FROM property_market_transaction
                WHERE transaction_id<>? AND lawd_cd=? AND neighborhood=?
                  AND (? IS NULL OR area BETWEEN ? AND ?)
                  AND (cancel_deal_yn IS NULL OR cancel_deal_yn='' OR cancel_deal_yn='N')
                ORDER BY deal_date DESC, transaction_id DESC LIMIT 5
                """, (rs, n) -> {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("transactionId", rs.getLong("transaction_id")); m.put("aptName", rs.getString("apt_name"));
                    m.put("dealAmount", nullableLong(rs, "deal_amount")); m.put("area", decimal(rs.getBigDecimal("area")));
                    m.put("floor", rs.getString("floor_text"));
                    java.sql.Date date = rs.getDate("deal_date"); m.put("dealDate", date == null ? null : date.toLocalDate().toString());
                    return m;
                },
                transactionId, lawdCd, neighborhood, area, area == null ? 0 : area - 10, area == null ? 0 : area + 10);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("item", item); result.put("comparable", comparable); result.put("comparableCount", comparable.size());
        return result;
    }

    @Transactional
    public Map<String, Integer> importMarketTransactions(String sourceName, List<Map<String, Object>> items) {
        int inserted = 0, updated = 0, errors = 0;
        LocalDateTime started = LocalDateTime.now();
        for (Map<String,Object> item : items) {
            try {
                String sourceId = text(item, "sourceId");
                if (sourceId.isBlank()) throw new IllegalArgumentException("sourceId 누락");
                Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM property_market_transaction WHERE source_id=?", Integer.class, sourceId);
                jdbc.update("""
                    INSERT INTO property_market_transaction
                    (source_id,source_name,lawd_cd,deal_ymd,apt_name,neighborhood,jibun,road_name,latitude,longitude,deal_amount,area,floor_text,build_year,deal_date,dealing_type,cancel_deal_yn,source_updated_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                    source_name=VALUES(source_name), lawd_cd=VALUES(lawd_cd), deal_ymd=VALUES(deal_ymd), apt_name=VALUES(apt_name),
                    neighborhood=VALUES(neighborhood), jibun=VALUES(jibun), road_name=VALUES(road_name),
                    latitude=COALESCE(VALUES(latitude),latitude), longitude=COALESCE(VALUES(longitude),longitude), deal_amount=VALUES(deal_amount),
                    area=VALUES(area), floor_text=VALUES(floor_text), build_year=VALUES(build_year), deal_date=VALUES(deal_date),
                    dealing_type=VALUES(dealing_type), cancel_deal_yn=VALUES(cancel_deal_yn), source_updated_at=VALUES(source_updated_at), updated_at=VALUES(updated_at)
                    """,
                    sourceId, blankDefault(sourceName, "MOLIT_APT_TRADE"), text(item,"lawdCd"), text(item,"dealYmd"), text(item,"aptName"),
                    text(item,"neighborhood"), text(item,"jibun"), text(item,"roadName"), objectNumber(item.get("lat")), objectNumber(item.get("lng")), objectNullableLong(item.get("dealAmount")),
                    objectNumber(item.get("area")), text(item,"floor"), objectNullableInteger(item.get("buildYear")), nullableDate(item.get("dealDate")),
                    text(item,"dealingType"), text(item,"cancelDealYn"), Timestamp.valueOf(started), Timestamp.valueOf(started), Timestamp.valueOf(started));
                if (exists != null && exists > 0) updated++; else inserted++;
            } catch (Exception e) { errors++; }
        }
        jdbc.update("INSERT INTO property_crawl_history (source_name,started_at,finished_at,collected_count,inserted_count,updated_count,error_count,message) VALUES (?,?,?,?,?,?,?,?)",
                sourceName, Timestamp.valueOf(started), Timestamp.valueOf(LocalDateTime.now()), items.size(), inserted, updated, errors, errors == 0 ? "SUCCESS" : "PARTIAL");
        return Map.of("collected", items.size(), "inserted", inserted, "updated", updated, "errors", errors);
    }

    @Transactional(readOnly = true)
    public List<Long> favoriteIds(Long userId) {
        return jdbc.query("""
                SELECT f.property_id
                FROM property_favorite f
                JOIN property_listing p ON p.property_id=f.property_id AND p.status='active'
                WHERE f.user_id=?
                ORDER BY f.created_at DESC
                """, (rs, n) -> rs.getLong(1), userId);
    }

    @Transactional
    public void replaceFavorites(Long userId, List<Long> ids) {
        jdbc.update("DELETE FROM property_favorite WHERE user_id=?", userId);
        if (ids == null) return;
        List<Long> requested = ids.stream().filter(java.util.Objects::nonNull).filter(id -> id > 0).distinct().limit(500).toList();
        if (requested.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(requested.size(), "?"));
        List<Long> activeIds = jdbc.query("SELECT property_id FROM property_listing WHERE status='active' AND property_id IN (" + placeholders + ")",
                (rs, n) -> rs.getLong(1), requested.toArray());
        activeIds.forEach(id -> jdbc.update("INSERT INTO property_favorite (user_id,property_id) VALUES (?,?)", userId, id));
    }

    private Map<String, Object> findById(long id) {
        List<Map<String, Object>> rows = jdbc.query("SELECT * FROM property_listing WHERE property_id=?", (rs, n) -> toMap(rs.getLong("property_id"), rs.getString("deal_type"), rs.getString("building_type"), rs.getString("title"), rs.getString("address"), rs.getString("sido"), rs.getString("sigungu"), rs.getString("neighborhood"), decimal(rs.getBigDecimal("latitude")), decimal(rs.getBigDecimal("longitude")), nullableLong(rs, "sale_price"), rs.getLong("deposit"), rs.getLong("monthly_rent"), rs.getLong("maintenance_fee"), decimal(rs.getBigDecimal("area")), rs.getString("floor_text"), rs.getBoolean("parking"), rs.getBoolean("elevator"), rs.getBoolean("pet"), rs.getString("description"), rs.getString("contact"), rs.getString("source_type"), rs.getString("source_name"), rs.getBoolean("study_data"), rs.getString("source_url"), rs.getTimestamp("last_seen_at"), rs.getTimestamp("updated_at")), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("매물을 찾을 수 없습니다.");
        return rows.get(0);
    }

    private Map<String, Object> toMap(long id, String dealType, String buildingType, String title, String address, String sido, String sigungu, String neighborhood,
            Double lat, Double lng, Long salePrice, long deposit, long monthly, long maintenance, Double area, String floor, boolean parking, boolean elevator, boolean pet,
            String description, String contact, String sourceType, String sourceName, boolean studyData, String sourceUrl, Timestamp lastSeenAt, Timestamp updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("dealType", dealType); m.put("deal", dealType.toLowerCase()); m.put("type", buildingType); m.put("buildingType", buildingType);
        m.put("title", title); m.put("address", address); m.put("sido", sido); m.put("district", sigungu); m.put("neighborhood", neighborhood); m.put("lat", lat); m.put("lng", lng);
        m.put("salePrice", salePrice); m.put("deposit", deposit); m.put("monthly", monthly); m.put("maintenance", maintenance); m.put("area", area); m.put("floor", floor);
        m.put("parking", parking); m.put("elevator", elevator); m.put("pet", pet); m.put("description", description); m.put("contact", maskContact(contact)); m.put("sourceType", sourceType);
        m.put("sourceName", sourceName); m.put("studyData", studyData);
        m.put("source", studyData ? "ZipAI 학습용" : (sourceType.equals("CRAWLING") ? blankDefault(sourceName, "수집 매물") : "직접 등록"));
        m.put("sourceUrl", sourceUrl); m.put("lastSeenAt", lastSeenAt == null ? null : lastSeenAt.toLocalDateTime().toString());
        m.put("verifiedAt", updatedAt == null ? null : updatedAt.toLocalDateTime().toLocalDate().toString());
        List<String> imageUrls = jdbc.query("SELECT image_url FROM property_listing_image WHERE property_id=? ORDER BY representative DESC,sort_order,image_id", (rs,n)->rs.getString(1), id);
        m.put("imageUrls", imageUrls); m.put("imageUrl", imageUrls.isEmpty() ? null : imageUrls.get(0)); m.put("photos", imageUrls.size());
        m.put("tags", List.of()); m.put("options", List.of()); m.put("safe", 80); m.put("tone", (id % 5) + 1);
        return m;
    }

    private long insertProperty(Long ownerId, String sourceType, String sourceId, String sourceUrl, String dealType, String buildingType, String title, String address,
            String sido, String sigungu, String neighborhood, Double lat, Double lng, Long salePrice, long deposit, long monthly, long maintenance, Double area, String floor,
            boolean parking, boolean elevator, boolean pet, String description, String contact, LocalDateTime now) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO property_listing (owner_user_id,source_type,source_id,source_url,deal_type,building_type,title,address,sido,sigungu,neighborhood,latitude,longitude,sale_price,deposit,monthly_rent,maintenance_fee,area,floor_text,parking,elevator,pet,description,contact,status,source_updated_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'active',?,?,?)
                """, Statement.RETURN_GENERATED_KEYS);
            Object[] a = {ownerId,sourceType,sourceId,sourceUrl,dealType,blankDefault(buildingType,"기타"),title.trim(),address.trim(),sido,sigungu,neighborhood,lat,lng,salePrice,deposit,monthly,maintenance,area,floor,parking,elevator,pet,description,contact,Timestamp.valueOf(now),Timestamp.valueOf(now),Timestamp.valueOf(now)};
            for (int i=0;i<a.length;i++) ps.setObject(i+1,a[i]);
            return ps;
        }, key);
        Number n = key.getKey();
        if (n == null) throw new IllegalStateException("매물 번호를 생성하지 못했습니다.");
        return n.longValue();
    }

    private void saveImages(long propertyId, List<MultipartFile> files, int representativeIndex) {
        for (int i=0;i<files.size();i++) {
            var image = images.store(files.get(i));
            jdbc.update("INSERT INTO property_listing_image (property_id,image_url,original_name,stored_name,sort_order,representative) VALUES (?,?,?,?,?,?)",
                    propertyId, image.imageUrl(), image.originalName(), image.storedName(), i, i==representativeIndex);
        }
    }

    private void requireOwnedListing(Long userId, long propertyId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM property_listing WHERE property_id=? AND owner_user_id=? AND source_type='USER'",
                Integer.class, propertyId, userId);
        if (count == null || count == 0) throw new IllegalArgumentException("관리할 수 있는 등록 매물을 찾지 못했습니다.");
    }

    private List<String> storedImageNames(long propertyId) {
        return jdbc.query("SELECT stored_name FROM property_listing_image WHERE property_id=? AND stored_name IS NOT NULL",
                (rs, n) -> rs.getString(1), propertyId);
    }

    private static List<MultipartFile> nonEmpty(MultipartFile[] files) { List<MultipartFile> out=new ArrayList<>(); if(files!=null) for(var f:files) if(f!=null&&!f.isEmpty()) out.add(f); return out; }
    private static void require(Map<String,String> f,String...keys){for(String k:keys)if(value(f,k).isBlank())throw new IllegalArgumentException(k+" 항목을 입력해 주세요.");}
    private static String value(Map<String,String> m,String k){return m.getOrDefault(k,"").trim();}
    private static String blankDefault(String v,String d){return v==null||v.isBlank()?d:v;}
    private static Double number(Map<String,String> m,String k){String v=value(m,k);return v.isBlank()?null:Double.valueOf(v);}
    private static Long longNumber(Map<String,String> m,String k){String v=value(m,k);return v.isBlank()?null:Long.valueOf(v);}
    private static long longNumberDefault(Map<String,String> m,String k,long d){Long v=longNumber(m,k);return v==null?d:v;}
    private static int intNumberDefault(Map<String,String> m,String k,int d){String v=value(m,k);if(v.isBlank())return d;try{return Integer.parseInt(v);}catch(NumberFormatException e){return d;}}
    private static boolean bool(Map<String,String> m,String k){return "true".equalsIgnoreCase(value(m,k))||"on".equalsIgnoreCase(value(m,k));}
    private static String text(Map<String,Object> m,String k){Object v=m.get(k);return v==null?"":String.valueOf(v).trim();}
    private static Double objectNumber(Object v){if(v==null||String.valueOf(v).isBlank())return null;return Double.valueOf(String.valueOf(v));}
    private static long objectLong(Object v){if(v==null||String.valueOf(v).isBlank())return 0L;return Long.parseLong(String.valueOf(v));}
    private static long validMaintenance(long value){if(value<0||value>1000)throw new IllegalArgumentException("관리비는 만원 단위로 0~1000 사이여야 합니다.");return value;}
    private static boolean boolObject(Object v){return v instanceof Boolean b?b:"true".equalsIgnoreCase(String.valueOf(v));}
    private static String maskContact(String value){
        if(value==null||value.isBlank())return "";
        String digits=value.replaceAll("\\D","");
        if(digits.length()<7)return "***";
        return digits.substring(0,3)+"-****-"+digits.substring(digits.length()-4);
    }

    private static Long objectNullableLong(Object v){if(v==null||String.valueOf(v).isBlank())return null;String s=String.valueOf(v).replace(",","").trim();return Long.valueOf(s);}
    private static Integer objectNullableInteger(Object v){if(v==null||String.valueOf(v).isBlank())return null;return Integer.valueOf(String.valueOf(v).trim());}
    private static java.sql.Date nullableDate(Object v){if(v==null||String.valueOf(v).isBlank())return null;return java.sql.Date.valueOf(String.valueOf(v).trim());}
    private static Double decimal(BigDecimal v){return v==null?null:v.doubleValue();}
    private static Long nullableLong(java.sql.ResultSet rs,String c)throws java.sql.SQLException{long v=rs.getLong(c);return rs.wasNull()?null:v;}
}
