package com.moviemanagerexam.dao;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;

import java.sql.Connection;

public class ConnectionManager {
    public Connection getConnection() throws SQLServerException {
        SQLServerDataSource ds;
        ds = new SQLServerDataSource();
        ds.setDatabaseName("PrivateMovieCollection1");
        ds.setUser("CS2025b_e_5");
        ds.setPassword("CS2025bE5#23");
        ds.setServerName("EASV-DB4");
        ds.setPortNumber(1433);
        ds.setTrustServerCertificate(true);
        ds.setLoginTimeout(30);
        ds.setSocketTimeout(30000);
        ds.setConnectRetryCount(3);
        ds.setConnectRetryInterval(2);
        return ds.getConnection();
    }
}
