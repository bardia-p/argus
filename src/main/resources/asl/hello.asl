// Constant beliefs

lowHealthThreshold(0.25).
searchTimeout(1000).
zombieDefenceLimit(2).

// Goals

!loop.

// Rules

isTargetPlayerNearby(Player) :-
    near(player, NearbyPlayers) &
    isMemberOf(Player, NearbyPlayers).

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

// Handling message communication
+message(askIf, Sender, wantAlliance(Sender)) <-
    say("Received an alliance request..");
    !addAlly(Sender);
    .my_name(AgName);
    .send(Sender, tell, wantAlliance(AgName)).

+message(tell, Sender, wantAlliance(Sender)) <-
    say("Formed an alliance.");
    !addAlly(Sender).

// Forming alliances
+!loop: not(allies(Allies)) & not(attempted_alliance) <-
    say("Asking to form an alliance...");
    .my_name(AgName);
    !broadcast(askIf, wantAlliance(AgName));
    +attempted_alliance;
    !loop.

+!addAlly(NewAlly): allies(Allies) <-
    .concat(Allies, [NewAlly], NewAllies);
    -allies(Allies);
    +allies(NewAllies).

+!addAlly(NewAlly) <-
    +allies([NewAlly]).

// Entering/Leaving Houses
+!loop: hiding & hasSufficientHealth <-
    say("Leaving my house..");
    leave_house;
    !loop.

+!loop: hiding <-
    say("I am hiding in my house to recover..");
    .my_name(AgName);
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

+!loop: damagedBy(Player) & not(damagedBy(zombie)) & isTargetPlayerNearby(Player) & needsRecovery <-
    say("Escaping from another player!!");
    escape(Player);
    !loop.

+!loop: damagedBy(Player) & isTargetPlayerNearby(Player) <-
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

// Helpers

// List helpers
isMemberOf(Item, [Item | _]).
isMemberOf(Item, [_ | Rest]) :-
    isMemberOf(Item, Rest).

// Messaging helpers
+!broadcast(MsgType, MsgCont): allPlayers(Players) <-
    !send_to_group(Players, MsgType, MsgCont).
+!send_to_group([], _, _) <- true.
+!send_to_group([Player | Rest], MsgType, MsgCont) <-
    .send(Player, MsgType, MsgCont);
    !send_to_group(Rest, MsgType, MsgCont).
