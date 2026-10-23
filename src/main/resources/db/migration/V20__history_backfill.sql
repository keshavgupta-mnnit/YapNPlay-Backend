-- History rows saved before V15 reached the server have no names, avatars or
-- coins (the columns came with their defaults). Fill them from what is known;
-- only rows still blank are touched.

-- Players: their account's name and avatar.
UPDATE match_result_seats s
SET display_name = u.display_name, avatar_id = u.avatar_id
FROM users u
WHERE s.user_id = u.id AND s.display_name = '';

-- CPUs: "CPU n" in seat order, avatars alternating as the server picks them (a09, a18).
UPDATE match_result_seats s
SET display_name = 'CPU ' || c.n, avatar_id = CASE WHEN c.n % 2 = 1 THEN 'a09' ELSE 'a18' END
FROM (
    SELECT match_id, seat, ROW_NUMBER() OVER (PARTITION BY match_id ORDER BY seat) AS n
    FROM match_result_seats
    WHERE user_id IS NULL
) c
WHERE s.match_id = c.match_id AND s.seat = c.seat AND s.user_id IS NULL AND s.display_name = '';

-- Coins: what the coin ledger paid for that match (pot share, finishing coins, entry).
UPDATE match_result_seats s
SET coins_won = l.total
FROM (SELECT user_id, match_id, SUM(delta) AS total FROM coin_ledger WHERE reason = 'WIN' AND match_id IS NOT NULL GROUP BY user_id, match_id) l
WHERE s.user_id = l.user_id AND s.match_id = l.match_id AND s.coins_won = 0;

UPDATE match_result_seats s
SET completion_coins = l.total
FROM (SELECT user_id, match_id, SUM(delta) AS total FROM coin_ledger WHERE reason = 'COMPLETION' AND match_id IS NOT NULL GROUP BY user_id, match_id) l
WHERE s.user_id = l.user_id AND s.match_id = l.match_id AND s.completion_coins = 0;

UPDATE match_result_seats s
SET stake = -l.total
FROM (SELECT user_id, match_id, SUM(delta) AS total FROM coin_ledger WHERE reason = 'ENTRY' AND match_id IS NOT NULL GROUP BY user_id, match_id) l
WHERE s.user_id = l.user_id AND s.match_id = l.match_id AND s.stake = 0;
