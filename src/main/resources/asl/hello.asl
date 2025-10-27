buildHouseWoodRequirement(30).

!start.

+!start <-
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