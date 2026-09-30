package com.onrender.zipai.safety.service;

import java.util.Optional;

public interface SafetyCoordinateGeocoder {
    Optional<Coordinate> geocode(String query);

    record Coordinate(
        String address,
        double latitude,
        double longitude,
        String sidoName,
        String sigunguName
    ) {
        public Coordinate(String address, double latitude, double longitude) {
            this(address, latitude, longitude, null, null);
        }
    }
}
