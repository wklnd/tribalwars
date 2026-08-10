package com.twlan.backend.domain;

// Append new constants only (the column is a database ENUM, widened by SchemaMigration).
public enum MovementType {
    ATTACK,
    RETURN,
    // Troops sent to another village of the same player, where they stay.
    SUPPORT
}
