-- 1. Criação do schema
CREATE DATABASE IF NOT EXISTS spvg_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE spvg_db;

-- 2. Tabela de Leituras
CREATE TABLE IF NOT EXISTS leituras (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  mac           VARCHAR(17)  NOT NULL,
  timestamp     DATETIME(3)  NOT NULL,
  gas           DOUBLE       NOT NULL,
  temperature   DOUBLE       NOT NULL,
  pressure      DOUBLE       NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_leituras_mac_ts (mac, timestamp)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 3. Tabela de Logs de Acionamento
CREATE TABLE IF NOT EXISTS logs (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  mac          VARCHAR(17)   NOT NULL,
  timestamp    DATETIME(3)   NOT NULL,
  state        ENUM('OPEN', 'CLOSE') NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_logs_mac_ts (mac, timestamp)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

