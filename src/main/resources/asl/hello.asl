/* Beliefs */

// Constants
lowHealthThreshold(0.25).
searchTimeout(1000).
buildRequirement(donation,2).

/* Goals */

!loop.

/* Plans */

// Handling message communication
+message(askIf, Sender, wantAlliance(Sender)) <-
    say("Received an alliance request from ", Sender);
    +ally(Sender);
    .my_name(AgName);
    .send(Sender, tell, allianceConfirmation(AgName)).
+message(tell, Sender, allianceConfirmation(Sender)) <-
    say("Formed an alliance with ", Sender);
    +ally(Sender).
+message(tell, Sender, endAlliance(Sender)) <-
    say("Ended an alliance with ", Sender);
    -ally(Sender).
+message(tell, Sender, hasHouse(Sender)) <-
    say(Sender, " has a house!!");
    receive(house, Sender).
+message(askIf, Sender, need(wood)) <-
    say(Sender, " need woods donation!!");
    +allyNeedsWoodDonation(Sender).
+message(tell, Sender, donated(wood, Woods)) <-
    say("Received wood donation from ", Sender, " :)");
    -askedForDonation;
    receive(wood, Woods).

// Messaging helpers
+!broadcast(MsgType, MsgCont): allPlayers(Players) <-
    !sendToGroup(Players, MsgType, MsgCont).
+!sendToGroup([], _, _) <- !loop.
+!sendToGroup([Player | Rest], MsgType, MsgCont): .my_name(AgName) & Player == AgName <-
    !sendToGroup(Rest, MsgType, MsgCont).
+!sendToGroup([Player | Rest], MsgType, MsgCont) <-
    .send(Player, MsgType, MsgCont);
    !sendToGroup(Rest, MsgType, MsgCont).

/* MAIN LOGIC LOOP */

// Asking for alliance
+!loop: not(ally(_)) & not(attempted_alliance) <-
    +attempted_alliance;
    say("Asking to form an alliance...");
    .my_name(AgName);
    !broadcast(askIf, wantAlliance(AgName)).

// Entering/Leaving Houses
+!loop: hiding & health(Health) & hasSufficientHealth(Health) <-
    say("Leaving my house..");
    leave_house;
    !loop.
+!loop: hiding <-
    say("Hiding in a house to recover..");
    .my_name(AgName);
    !loop.
+!loop: houseCount(NumHouses) & health(Health) & needsRecovery(Health) <-
    say("Heading to a house to recover..");
    enter_house;
    !loop.

// Attacking/Defending against players/zombies

+!loop: health(Health) & ((damagedBy(zombie) & not(needsRecovery(Health))) |
(near(zombie,NumZombies) & canSurviveZombies(Health, NumZombies))) <-
    +attempted_attack;
    say("Fighting zombies!!!");
    attack(zombie);
    -attempted_attack;
    !loop.
+!loop: near(zombie,NumZombies) | damagedBy(zombie) <-
    +attempted_escape;
    say("Escaping zombies...");
    escape;
    -attempted_escape;
    !loop.

+!loop: damagedBy(Player) & not(damagedBy(zombie)) & isTargetPlayerNearby(Player) & health(Health) &
needsRecovery(Health) <-
    +attempted_escape;
    say("Escaping from ", Player, "!!");
    escape;
    -attempted_escape;
    !loop.
+!loop: damagedBy(Player) & isTargetPlayerNearby(Player) <-
    +attempted_attack;
    say("Defending against ", Player, "!!");
    attack(Player);
    -attempted_attack;
    !loop.
+!loop: near(player,NearbyPlayers) & health(Health) & not(needsRecovery(Health)) & hasWeapon(_) &
not(getEnemyPlayers(NearbyPlayers,[])) & getEnemyPlayers(NearbyPlayers, [EnemyPlayer|_]) <-
    +attempted_attack;
    say("Attacking ", EnemyPlayer, "!!!");
    attack(EnemyPlayer);
    -attempted_attack;
    !loop.
+!loop: allPlayers(Players) & health(Health) & hasSufficientHealth(Health) & hasWeapon(_) &
not(getEnemyPlayers(Players,[])) & getEnemyPlayers(Players, [EnemyPlayer|_]) & searchTimeout(SEARCH_TIMEOUT) <-
    +attempted_player_search;
    say("Looking for ", EnemyPlayer, "!!!");
    find(EnemyPlayer);
    .wait({+near(player,_)}, SEARCH_TIMEOUT, EventTime);
    -attempted_player_search;
    !loop.
+!loop: health(Health) & hasSufficientHealth(Health) & hasWeapon(_) & searchTimeout(SEARCH_TIMEOUT) <-
    +attempted_zombie_search;
    say("Looking for zombies!!!");
    find(zombie);
    .wait({+near(zombie,_)}, SEARCH_TIMEOUT, EventTime);
    -attempted_zombie_search;
    !loop.

// Building houses/weapons
+!loop: woodsChopped(Woods) & hasEnoughWoodFor(Woods, house) <-
    say("Building a house...");
    build(house);
    .my_name(AgName);
    .findall(Ally, ally(Ally), Allies);
    !sendToGroup(Allies, tell, hasHouse(AgName)).
+!loop: woodsChopped(Woods) & hasEnoughWoodFor(Woods, sword) & not(hasWeapon(sword)) <-
    say("Building a sword...");
    build(sword);
    !loop.

// Donating wood
+!loop: woodsChopped(Woods) & allyNeedsWoodDonation(Ally) & isTargetPlayerNearby(Ally) & hasEnoughWoodFor(Woods,donation)
 & buildRequirement(donation,WOOD_DONATION_LIMIT) <-
    say("Donating wood to ", Ally, "..");
    donate_wood(WOOD_DONATION_LIMIT);
    .send(Ally, tell, donated(wood, WOOD_DONATION_LIMIT));
    -allyNeedsWoodDonation(Ally);
    !loop.

// Chopping wood
+!loop: near(tree) <-
    say("Chopping wood!!");
    chop_wood;
    !loop.

// Getting wood
+!loop: searchTimeout(SEARCH_TIMEOUT) <-
    +attempted_tree_search;
    say("Looking for trees..");
    find(tree);
    .wait({+near(tree)}, SEARCH_TIMEOUT, EventTime);
    -attempted_tree_search;
    !loop.

// Handling Failed plans.
-!loop: attempted_attack <-
    -attempted_attack;
    say("Failed to attack, escaping now!");
    escape;
    !loop.
-!loop: attempted_escape <-
    -attempted_escape;
    say("Failed to escape, trying again!");
    escape;
    !loop.
-!loop: (attempted_player_search | attempted_zombie_search) & searchTimeout(SEARCH_TIMEOUT) <-
    -attempted_player_search;
    -attempted_zombie_search;
    +attempted_tree_search;
    say("Looking for trees..");
    find(tree);
    .wait({+near(tree)}, SEARCH_TIMEOUT, EventTime);
    -attempted_tree_search;
    !loop.
-!loop: attempted_tree_search & not(askedForDonation) <-
    -attempted_tree_search;
    +askedForDonation;
    .findall(Ally, ally(Ally), Allies);
    say("Failed to find wood :( Asking for donation from ", Allies);
    !sendToGroup(Allies, askIf, need(wood)).
-!loop <- !loop.

/* Rules */

getEnemyPlayers([], Result)  :- Result = [].
getEnemyPlayers([Player | Rest], Result) :-
    not(ally(Player)) &
    getEnemyPlayers(Rest, RemainingResult) &
    Result = [Player | RemainingResult].
getEnemyPlayers([Player | Rest], Result) :-
    ally(Player) &
    getEnemyPlayers(Rest, RemainingResult) &
    Result = RemainingResult.

isTargetPlayerNearby(Player) :-
    near(player, NearbyPlayers) &
    isMemberOf(Player, NearbyPlayers).

hasSufficientHealth(Health) :-
    lowHealthThreshold(LOW_HEALTH_THRESHOLD) &
    Health >= 2 * LOW_HEALTH_THRESHOLD.

needsRecovery(Health) :-
    lowHealthThreshold(LOW_HEALTH_THRESHOLD) &
    Health <= LOW_HEALTH_THRESHOLD.

canSurviveZombies(Health, NumZombies) :-
    zombieDefenceLimit(ZOMBIE_DEFENCE_LIMIT) &
    not(needsRecovery(Health)) &
    NumZombies <= ZOMBIE_DEFENCE_LIMIT.

hasEnoughWoodFor(Woods, Object) :-
    buildRequirement(Object,OBJECT_WOOD_REQUIREMENT) &
    Woods >= OBJECT_WOOD_REQUIREMENT.

isMemberOf(Item, [Head | _]) :- Item == Head.
isMemberOf(Item, [_ | Rest]) :- isMemberOf(Item, Rest).