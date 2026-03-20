package com.terra.api.game.accounts.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class GameAccountRowMapper implements RowMapper<Map<String, Object>> {
    @Override
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("login", rs.getString("login"));
        map.put("email", rs.getString("email"));
        map.put("createdAt", rs.getTimestamp("created_time") != null
                ? rs.getTimestamp("created_time").toInstant()
                : Instant.now());
        return map;
    }
}

