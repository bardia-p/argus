/* Beliefs */

// Constants
lowHealthThreshold(0.25).
searchTimeout(1000).
buildRequirement(donation,2).

// Variables
allies([]).

/* Goals */

!loop.

/* Plans */

// Handling message communication
+message(askIf, Sender, wantAlliance(Sender)): not(isPlayerAnAlly(Sender)) <-
    say("Received an alliance request..");
    !addAlly(Sender);
    .my_name(AgName);
    .send(Sender, tell, wantAlliance(AgName)).
+message(tell, Sender, wantAlliance(Sender)): not(isPlayerAnAlly(Sender)) <-
    say("Formed an alliance.");
    !addAlly(Sender).
+message(askIf, Sender, needWood) <-
    say("Ally need woods donation!!");
    +allyNeedsWoodDonation(Sender).
+message(tell, Sender, donatedWoods(Woods)) <-
    say("Received wood donation!!");
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

// Forming alliance
+!addAlly(NewAlly): allies(Allies) <-
    .concat(Allies, [NewAlly], NewAllies);
    -+allies(NewAllies).

/* MAIN LOGIC LOOP */

// Asking for alliance
+!loop: allies([]) & not(attempted_alliance) <-
    say("Asking to form an alliance...");
    .my_name(AgName);
    !broadcast(askIf, wantAlliance(AgName));
    +attempted_alliance;
    !loop.

// Entering/Leaving Houses
+!loop: hiding & health(Health) & hasSufficientHealth(Health) <-
    say("Leaving my house..");
    leave_house;
    !loop.
+!loop: hiding <-
    say("I am hiding in my house to recover..");
    .my_name(AgName);
    !loop.
+!loop: houseCount(NumHouses) & health(Health) & needsRecovery(Health) <-
    say("Heading to my house to recover..");
    enter_house;
    !loop.

// Attacking/Defending against players/zombies

+!loop: near(zombie,NumZombies) & health(Health) & canSurviveZombies(Health, NumZombies) <-
    say("Fighting zombies!!!");
    attack(zombie);
    !loop.
+!loop: near(zombie,NumZombies) <-
    say("Escaping zombies...");
    escape;
    !loop.

+!loop: near(players,[Player | _]) & health(Health) & not(needsRecovery(Health)) & not(isPlayerAnAlly(Sender)) <-
    say("Attacking an enemy player...");
    attack(Player);
    !loop.
+!loop: damagedBy(Player) & not(damagedBy(zombie)) & isTargetPlayerNearby(Player) & health(Health) & needsRecovery(Health) <-
    say("Escaping from another player!!");
    escape;
    !loop.
+!loop: damagedBy(Player) & isTargetPlayerNearby(Player) <-
    say("Defending against another player!!");
    attack(Player);
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
    say("Donating wood to an ally..");
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
-!loop: allies(Allies) & not(allies([])) & not(askedForDonation) <-
    say("Asking for wood donation..");
    +askedForDonation;
    !sendToGroup(Allies, askIf, needWood).
-!loop <- !loop.

/* Rules */

isPlayerAnAlly(Player) :-
    allies(Allies) &
    isMemberOf(Player, Allies).

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

isMemberOf(_, []) :- false.
isMemberOf(Item, [Head | _]) :- Item == Head.
isMemberOf(Item, [_ | Rest]) :-
    isMemberOf(Item, Rest).