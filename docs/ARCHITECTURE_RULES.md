# YapNPlay backend: architecture rules

Rules every change to the server follows. The app has the same rule for its
mirrored game engines (app repo `docs/ARCHITECTURE_RULES.md` §5.9).

## Game bots decide like a person

Applies to every computer decision in every game, now and later: CPU seats,
Autoplay, away or bot-held seats.

1. **Profit, loss and risk, never a plain if/else.** No decision rests on a
   single fixed threshold.
2. **One shape for every decision.** List the options the rules allow, then
   give each an `Evaluation(gain, cost, risk, note)`:
   - `gain`: likely future earnings or progress;
   - `cost`: paid now;
   - `risk`: chance of a loss × its size (being captured, paying rent, going
     broke).

   Pick the highest `gain − cost − risk × riskWeight`. Use the game's real
   odds (dice distributions) and the match so far. Implement it as
   `BotDecision<T>` (`games/base/play/BotDecision.kt`).
3. **Cushions are risk, not rules.** Cash reserves and safety margins live
   inside the risk term (e.g. City Bazaar: chance of running short in the
   next two turns × net worth at stake).
4. **Standing.** The risk weight is scaled by the seat's standing
   (`Standing.factor`): far behind the leader → bolder, leading → careful.
   Settings `bot.standing_min_pct` / `bot.standing_max_pct`.
5. **The match's own rules.** The bot follows the match's rules, house rules
   included. For example, a Ludo pair only protects when pair protection is
   on, and is never a goal in itself.
6. **Weights are runtime settings** (admin "Game settings"), never constants
   that need a deploy.
7. **Explainable.** Every decision records its top options with
   gain/cost/risk in the match's `BotTrace` (debug log, plus the last 50 per
   match on the admin "Bots" tab).
8. **The server owns the bot; the app mirrors it.** Any change to a bot is
   sent to the app session with the exact rules.
9. **Tests read like a player's choice.** For example, "with 10,000 and
   heavy rent ahead it won't buy an 8,500 city".
10. **Pick defaults with the arena.**
    `./gradlew arena -Parena="game=citybazaar games=200 B.<field>=<n>"` runs
    seeded bot-vs-bot games for two setting sets and compares win rates.

### Scenarios each game's bot must cover

**City Bazaar**
- Every bill ahead: rent, taxes (the wealth tax rises after a build), Club
  (pay everyone), Rest House (everyone pays you).
- Denial: a city is worth what keeping it from a rival who needs it for a set
  is worth. Selling such a city in debt counts that boost to the rival.
- Opportunist bids below price on a rival's debt auction.
- Ruin odds over the next two turns. Building pressure on a rival close to
  going broke.
- Jail: pay or stay by value. Stay when short of cash. The rent dodged in jail
  is mostly only postponed.

**Royal Ludo**
- Hit odds: a piece 1–6 behind, a piece 7–12 behind (a six then the rest),
  and a six from base.
- Home-lane pieces are safe and are moved only to finish.
- An extra piece out is worth more early in the game.
- Extra turns from the rules have a value.
- Capturing the race leader is worth more.
