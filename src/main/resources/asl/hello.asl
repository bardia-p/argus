// Constant beliefs
lowHealthThreshold(0.25).
searchTimeout(1000).
zombieDefenceLimit(1).

!loop.

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
    NumZombies <= ZOMBIE_DEFENCE_LIMIT.

hasEnoughWoodFor(Object) :-
    woodsChopped(Woods) &
    buildRequirement(Object,OBJECT_WOOD_REQUIREMENT) &
    Woods >= OBJECT_WOOD_REQUIREMENT.

+!loop: hiding & needsRecovery <-
    say("I AM HIDING IN MY HOUSE!");
    .my_name(AgName);
    .broadcast(tell, hasHouse(AgName));
    !loop.

+!loop: hiding & hasSufficientHealth <-
    say("LEAVING MY HOUSE!");
    leave_house;
    !loop.

+!loop: houseCount(NumHouses) & needsRecovery <-
    say("Entering my house..");
    enter_house;
    !loop.

+!loop: near(zombies,NumZombies) & canSurviveZombies(NumZombies) <-
    say("Fighting zombies!!!");
    attack(zombies);
    !loop.

+!loop: near(zombies,NumZombies) <-
    say("Escaping zombies...");
    escape(zombies);
    !loop.

+!loop: hasEnoughWoodFor(house) <-
    say("Building a house...");
    build(house);
    !loop.

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