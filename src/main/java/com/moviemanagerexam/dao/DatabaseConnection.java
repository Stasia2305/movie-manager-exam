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

    private static synchronized void initializeDatabaseInternal() throws SQLException {
        if (initialized) {
            return;
        }

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            if (!tableExists(stmt, "categories")) {
                stmt.execute("CREATE TABLE categories (" +
                        "id INT PRIMARY KEY AUTOINCREMENT," +
                        "name NVARCHAR(100) NOT NULL UNIQUE)");
            }

            if (!tableExists(stmt, "movies")) {
                stmt.execute("CREATE TABLE movies (" +
                        "id INT PRIMARY KEY AUTOINCREMENT," +
                        "title NVARCHAR(255) NOT NULL," +
                        "imdb_rating FLOAT," +
                        "personal_rating INT," +
                        "file_path NVARCHAR(500) NOT NULL," +
                        "last_view DATETIME)");
            }

            if (!tableExists(stmt, "movie_category")) {
                stmt.execute("CREATE TABLE movie_category (" +
                        "movie_id INT NOT NULL," +
                        "category_id INT NOT NULL," +
                        "PRIMARY KEY (movie_id, category_id)," +
                        "FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE)");
            }

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

    private static boolean tableExists(Statement stmt, String tableName) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + tableName + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
