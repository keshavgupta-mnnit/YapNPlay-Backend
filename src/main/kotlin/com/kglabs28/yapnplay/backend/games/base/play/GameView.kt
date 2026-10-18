package com.kglabs28.yapnplay.backend.games.base.play

/**
 * What a specific viewer (player or spectator) sees of the game state.
 * Each game defines its own view type as a @Serializable class.
 * The server serializes it to JSON and sends it in MatchSnapshot.
 *
 * Hidden-information rule: a view contains ONLY what that viewer may
 * know. Never send hidden data and rely on the app to hide it.
 * - Player: the public table plus their own private items (their hand).
 * - Spectator: the public table only (e.g. UNO: discard pile, whose turn,
 *   how many cards each player holds — never the cards themselves).
 * Games with no hidden information (Royal Ludo) give everyone the same view.
 */
interface GameView
