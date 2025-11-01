buildHouseWoodRequirement(30).

!loop.

hasEnoughWood :-
    woodsChopped(Woods) &
    buildHouseWoodRequirement(HOUSE_WOOD_REQUIREMENT) &
    Woods >= HOUSE_WOOD_REQUIREMENT.

+!loop: hasHouse <-
    say("I HAVE A HOUSE!!");
    .my_name(AgName);
    .broadcast(tell, hasHouse(AgName));
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

+!loop <-
    say("Looking for trees..");
    find_tree;
    !loop.

-!loop <- !loop.