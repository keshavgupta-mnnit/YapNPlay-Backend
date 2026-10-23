package com.kglabs28.yapnplay.backend.domain.feedback

/** What a player's message is about (the app's dropdown). */
enum class FeedbackKind { NEW_GAME, NEW_FEATURE, ISSUE, BUG, FEEDBACK }

/** OPEN until an admin replies; ANSWERED after (a new player message reopens it). */
enum class FeedbackStatus { OPEN, ANSWERED }
