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
+message(askIf, Sender, needWood) <-
    say(Sender, " need woods donation!!");
    +allyNeedsWoodDonation(Sender).
+message(tell, Sender, donatedWoods(Woods)) <-
    say("Received wood donation from ", Sender, " :)");
    -askedForDonation;
    receive_wood(Woods).

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
    say("Hiding in my house to recover..");
    .my_name(AgName);
    !loop.
+!loop: houseCount(NumHouses) & health(Health) & needsRecovery(Health) <-
    say("Heading to my house to recover..");
    enter_house;
    !loop.

// Attacking/Defending against players/zombies

+!loop: health(Health) & ((damagedBy(zombie) & not(needsRecovery(Health))) |
(near(zombie,NumZombies) & canSurviveZombies(Health, NumZombies))) <-
    say("Fighting zombies!!!");
    attack(zombie);
    !loop.
+!loop: near(zombie,NumZombies) | damagedBy(zombie) <-
    say("Escaping zombies...");
    escape;
    !loop.

+!loop: damagedBy(Player) & not(damagedBy(zombie)) & isTargetPlayerNearby(Player) & health(Health) &
needsRecovery(Health) <-
    say("Escaping from ", Player, "!!");
    escape;
    !loop.
+!loop: damagedBy(Player) & isTargetPlayerNearby(Player) <-
    say("Defending against ", Player, "!!");
    attack(Player);
    !loop.
+!loop: near(player,NearbyPlayers) & health(Health) & not(needsRecovery(Health)) & hasWeapon(_) &
not(getEnemyPlayers(NearbyPlayers,[])) & getEnemyPlayers(NearbyPlayers, [EnemyPlayer|_]) <-
    say("Attacking ", EnemyPlayer, "!!!");
    attack(EnemyPlayer);
    !loop.
+!loop: allPlayers(Players) & health(Health) & hasSufficientHealth(Health) & hasWeapon(_) &
not(getEnemyPlayers(Players,[])) & getEnemyPlayers(Players, [EnemyPlayer|_]) & searchTimeout(SEARCH_TIMEOUT) <-
    say("Looking for ", EnemyPlayer, "!!!");
    find(EnemyPlayer);
    .wait({+near(player,_)}, SEARCH_TIMEOUT, EventTime);
    !loop.

// Building houses/weapons
+!loop: woodsChopped(Woods) & hasEnoughWoodFor(Woods, house) <-
    say("Building a house...");
    build(house);
    !loop.
+!loop: woodsChopped(Woods) & hasEnoughWoodFor(Woods, sword) & not(hasWeapon(sword)) <-
    say("Building a sword...");
    build(sword);
    !loop.

// Donating wood
+!loop: woodsChopped(Woods) & allyNeedsWoodDonation(Ally) & isTargetPlayerNearby(Ally) & hasEnoughWoodFor(Woods,donation)
 & buildRequirement(donation,WOOD_DONATION_LIMIT) <-
    say("Donating wood to ", Ally, "..");
    donate_wood(WOOD_DONATION_LIMIT);
    .send(Ally, tell, donatedWoods(WOOD_DONATION_LIMIT));
    -allyNeedsWoodDonation(Ally);
    !loop.

// Chopping wood
+!loop: near(tree) <-
    say("Chopping wood!!");
    chop_wood;
    !loop.

// Getting wood
+!loop: searchTimeout(SEARCH_TIMEOUT) <-
    say("Looking for trees..");
    find(tree);
    .wait({+near(tree)}, SEARCH_TIMEOUT, EventTime);
    !loop.

// No Allies
-!loop: not(askedForDonation) <-
    .findall(Ally, ally(Ally), Allies);
    say("Asking for wood donation from ", Allies);
    +askedForDonation;
    !sendToGroup(Allies, askIf, needWood).
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