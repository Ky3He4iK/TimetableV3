package com.ky3he4ik

enum class Type(val data: Int) {
    CLASS(0),
    ROOM(1),
    TEACHER(2),
    USER(3),
    DAY(4),
    LESSON(5),
    SUB_GROUP(6)
}

enum class Presentation(val data: Int) {
    DEFAULT(0),
    ALL_WEEK(1),
    TODAY(2),
    TOMORROW(3),
    NEAR(4),
    ALL_CLASSES(5),
    CURRENT_CLASS(6),
    OTHER(7)
}

enum class FeedbackType(val data: Int) {
    DENIED(-1),
    UNREAD(0),
    WAITING(1),
    SOLVING(2),
    SOLVED(3)
}

enum class LoadType(val data: Int) {
    READ(0),
    CREATE(1)
}