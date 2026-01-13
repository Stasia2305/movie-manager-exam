package com.moviemanagerexam.dao;

import com.microsoft.sqlserver.jdbc.SQLServerException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static boolean initialized = false;
    private static final ConnectionManager connectionManager = new ConnectionManager();

    public DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            return connectionManager.getConnection();
        } catch (SQLServerException e) {
            String errorMsg = "Failed to get connection from SQL Server. ";
            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                errorMsg += "Connection timed out - check VPN connection and server availability.";
            } else {
                errorMsg += e.getMessage();
            }
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new SQLException(errorMsg, e);
        }
    }

    public static void initializeDatabase() throws SQLException {
        if (!initialized) {
            initializeDatabaseInternal();
        }
    }

    public static void initializeDatabaseAsync() {
        new Thread(() -> {
            try {
                initializeDatabase();
            } catch (SQLException e) {
                System.err.println("Warning: Failed to initialize database tables: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private static synchronized void initializeDatabaseInternal() throws SQLException {
        if (initialized) {
            return;
        }

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'categories') " +
                    "CREATE TABLE categories (" +
                    "id INT PRIMARY KEY IDENTITY(1,1)," +
                    "name NVARCHAR(100) NOT NULL UNIQUE)");

            stmt.execute("IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'movies') " +
                    "CREATE TABLE movies (" +
                    "id INT PRIMARY KEY IDENTITY(1,1)," +
                    "title NVARCHAR(255) NOT NULL," +
                    "imdb_rating FLOAT," +
                    "personal_rating INT," +
                    "file_path NVARCHAR(500) NOT NULL," +
                    "last_view DATETIME)");

            stmt.execute("IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'movie_category') " +
                    "CREATE TABLE movie_category (" +
                    "movie_id INT NOT NULL," +
                    "category_id INT NOT NULL," +
                    "PRIMARY KEY (movie_id, category_id)," +
                    "FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE)");

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String[] defaultCategories = {
                        "Action", "Adventure", "Animation", "Comedy", "Crime", 
                        "Documentary", "Drama", "Family", "Fantasy", "History", 
                        "Horror", "Music", "Mystery", "Romance", "Science Fiction", 
                        "TV Movie", "Thriller", "War", "Western"
                    };
                    for (String cat : defaultCategories) {
                        try (var pstmt = conn.prepareStatement("INSERT INTO categories (name) VALUES (?)")) {
                            pstmt.setString(1, cat);
                            pstmt.executeUpdate();
                        }
                    }
                    System.out.println("Default categories inserted successfully.");
                }
            }
            
            initialized = true;
        } catch (SQLServerException e) {
            throw new SQLException("Failed to initialize database: " + e.getMessage(), e);
        }
    }
}
