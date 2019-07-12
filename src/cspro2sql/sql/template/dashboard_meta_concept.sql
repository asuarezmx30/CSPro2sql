DROP TABLE IF EXISTS @SCHEMA.`dashboard_meta_concept`;
CREATE TABLE @SCHEMA.DASHBOARD_META_CONCEPT (
  `ID` int(3) NOT NULL,
  `NAME` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `NOTE` text,
  `ORDER` int(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (1, "household" ,"Household dictionary", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (2, "listing" ,"Listing dictionary", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (3, "expected" ,"Enumeration area dictionary, containing xpected Households from Cartograhpy", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (4, "territory" ,"Territory", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (5, "individual" ,"Individual record in household dictionary", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (6, "age" ,"Age variable", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (7, "sex" ,"Sex variable", null);
INSERT INTO @SCHEMA.`dashboard_meta_concept` (`ID`, `NAME`, `NOTE`, `ORDER`) 
    VALUES (8, "religion" ,"Religion variable", null);