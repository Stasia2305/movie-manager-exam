package com.moviemanagerexam.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MovieCategoryDAO {

    public List<String> getCategoriesForMovie(int movieId) throws SQLException {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT c.name FROM categories c JOIN movie_category mc ON c.id = mc.category_id WHERE mc.movie_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, movieId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(rs.getString("name"));
                }
            }
        }
        return categories;
    }

    public void addMovieToCategory(int movieId, int categoryId) throws SQLException {
        String sql = "MERGE INTO movie_category AS target " +
                "USING (SELECT ? AS movie_id, ? AS category_id) AS source " +
                "ON target.movie_id = source.movie_id AND target.category_id = source.category_id " +
                "WHEN NOT MATCHED THEN INSERT (movie_id, category_id) VALUES (source.movie_id, source.category_id);";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, movieId);
            pstmt.setInt(2, categoryId);
            pstmt.executeUpdate();
        }
    }

}
