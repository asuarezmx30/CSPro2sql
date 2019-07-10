DROP TABLE IF EXISTS @SCHEMA.DASHBOARD_INFO;
CREATE TABLE IF NOT EXISTS @SCHEMA.DASHBOARD_INFO (
    `ID` int(1) NOT NULL DEFAULT 0,
    `LISTING` int(1) NOT NULL DEFAULT 0,
    `EXPECTED` int(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`ID`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO @SCHEMA.DASHBOARD_INFO values (0, @LISTING, @EXPECTED);

