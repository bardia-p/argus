// Variable beliefs
inHouseCount(0).

// Constant beliefs
inHouseLimit(20).
treeSearchTimeout(1000).
zombieDefenceLimit(1).

!loop.

shouldStayInHouse(InHouseCount) :-
    inHouseLimit(IN_HOUSE_LIMIT) &
    InHouseCount < IN_HOUSE_LIMIT.

canSurviveZombies(NumZombies) :-
    zombieDefenceLimit(ZOMBIE_DEFENCE_LIMIT) &
    NumZombies <= ZOMBIE_DEFENCE_LIMIT.

hasEnoughWood :-
    woodsChopped(Woods) &
    buildHouseWoodRequirement(HOUSE_WOOD_REQUIREMENT) &
    Woods >= HOUSE_WOOD_REQUIREMENT.

+!loop: inHouse & inHouseCount(InHouseCount) & shouldStayInHouse(InHouseCount) <-
    -+inHouseCount(InHouseCount + 1);
    say("I AM HIDING IN MY HOUSE!");
    .my_name(AgName);
    .broadcast(tell, hasHouse(AgName));
    !loop.

+!loop: inHouse <-
    -+inHouseCount(0);
    say("LEAVING MY HOUSE!");
    leave_house;
    !loop.

+!loop: hasHouse(NumHouses) <-
    say("Entering my house..");
    enter_house;
    !loop.

+!loop: nearbyZombies(NumZombies) & canSurviveZombies(NumZombies) <-
    say("Fighting zombies!!!");
    attack_zombies;
    !loop.

+!loop: nearbyZombies(NumZombies) <-
    say("Escaping zombies...");
    escape_zombies;
    !loop.

+!loop: canSurviveZombies <-
    say("Fighting Zombies");
    attack_zombies;
    !loop.

+!loop: hasEnoughWood <-
    say("Building a house...");
    build_house;
    !loop.

+!loop: canSeeTree <-
    say("Chopping wood!!");
    chop_wood;
    !loop.

+!loop: treeSearchTimeout(TREE_SEARCH_TIMEOUT) <-
    say("Looking for trees..");
    find_tree;
    .wait({+canSeeTree}, TREE_SEARCH_TIMEOUT, EventTime);
    !loop.

-!loop <- !loop.