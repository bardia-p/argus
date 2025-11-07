buildHouseWoodRequirement(30).
treeSearchTimeout(1000).

!loop.

hasEnoughWood :-
    woodsChopped(Woods) &
    buildHouseWoodRequirement(HOUSE_WOOD_REQUIREMENT) &
    Woods >= HOUSE_WOOD_REQUIREMENT.

+!loop: inHouse <-
    say("I AM HIDING IN MY HOUSE!");
    .my_name(AgName);
    .broadcast(tell, hasHouse(AgName));
    !loop.

+!loop: hasHouse <-
    say("Entering my house..");
    enter_house;
    !loop.

+!loop: nearbyZombies(NumZombies) <-
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