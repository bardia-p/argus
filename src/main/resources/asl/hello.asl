// Constant beliefs

lowHealthThreshold(0.25).
searchTimeout(1000).
zombieDefenceLimit(2).

// Goals

!loop.

// Rules

isPlayerNearby(Player, [Player | _]).
isPlayerNearby(Player, [_ | Rest]) :-
    isPlayerNearby(Player, Rest).

hasSufficientHealth :-
    health(Health) &
    lowHealthThreshold(LOW_HEALTH_THRESHOLD) &
    Health >= 2 * LOW_HEALTH_THRESHOLD.

needsRecovery :-
    health(Health) &
    lowHealthThreshold(LOW_HEALTH_THRESHOLD) &
    Health <= LOW_HEALTH_THRESHOLD.

canSurviveZombies(NumZombies) :-
    zombieDefenceLimit(ZOMBIE_DEFENCE_LIMIT) &
    not(needsRecovery) &
    NumZombies <= ZOMBIE_DEFENCE_LIMIT.

hasEnoughWoodFor(Object) :-
    woodsChopped(Woods) &
    buildRequirement(Object,OBJECT_WOOD_REQUIREMENT) &
    Woods >= OBJECT_WOOD_REQUIREMENT.

// Plans

// Entering/Leaving Houses
+!loop: hiding & hasSufficientHealth <-
    say("Leaving my house..");
    leave_house;
    !loop.

+!loop: hiding <-
    say("I am hiding in my house to recover..");
    .my_name(AgName);
    .broadcast(tell, hasHouse(AgName));
    !loop.

+!loop: houseCount(NumHouses) & needsRecovery <-
    say("Heading to my house to recover..");
    enter_house;
    !loop.

// Attacking/Defending against players/zombies

+!loop: damagedBy(zombie) & near(zombie,NumZombies) & canSurviveZombies(NumZombies) <-
    say("Fighting zombies!!!");
    attack(zombie);
    !loop.

+!loop: damagedBy(zombie) & near(zombie,NumZombies) <-
    say("Escaping zombies...");
    escape(zombie);
    !loop.

+!loop: damagedBy(Player) & not(damagedBy(zombie)) & near(player, NearbyPlayers) & isPlayerNearby(Player, NearbyPlayers) & needsRecovery <-
    say("Escaping from another player!!");
    escape(Player);
    !loop.

+!loop: damagedBy(Player) & not(damagedBy(zombie)) & near(player, NearbyPlayers) & isPlayerNearby(Player, NearbyPlayers) <-
    say("Defending against another player!!");
    attack(Player);
    !loop.

// Building houses/weapons
+!loop: hasEnoughWoodFor(house) <-
    say("Building a house...");
    build(house);
    !loop.

+!loop: hasEnoughWoodFor(sword) & not(hasWeapon(sword)) <-
    say("Building a sword...");
    build(sword);
    !loop.

// Chopping wood
+!loop: near(tree) <-
    say("Chopping wood!!");
    chop_wood;
    !loop.

+!loop: searchTimeout(SEARCH_TIMEOUT) <-
    say("Looking for trees..");
    find(tree);
    .wait({+near(tree)}, SEARCH_TIMEOUT, EventTime);
    !loop.

-!loop <- !loop.