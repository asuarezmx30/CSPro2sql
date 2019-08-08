CREATE TABLE IF NOT EXISTS @SCHEMA.DASHBOARD_TOTAL (
  `SURVEY_DAY` int(3) NOT NULL,
  `TOTAL_FIELDWORK` int(11) DEFAULT NULL,
  `TOTAL_FRESHLIST` int(11) DEFAULT NULL
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT DAY(UPDATE_TIME) as SURVEY_DAY, max(EA_FIELDWORK) as TOTAL_FIELDWORK,  max(EA_FRESHLIST) as TOTAL_FRESHLIST  FROM dashboard_pilot_2.tr_total group by SURVEY_DAY order by SURVEY_DAY;
