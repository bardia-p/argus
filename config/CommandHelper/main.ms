@server_name = 'Argus';
@player_name = 'bardibloo'; # change this based on your username
@mapSizeMin = -25;
@mapSizeMax = 25;
@mapSizeStep = 5;
@numZombies = 8;

# Echo server greeting
console(
    'CommandHelper successfully installed! To get started, visit https://methodscript.com, and visit the learning'
    . ' trail at the bottom, and particularly the Beginner\'s and Advanced Guides.'
);

bind('player_join', null, null, @event, @player_name,
    run('/gamemode spectator');
    run('/tp '.@player_name.' ~ ~10 ~ 0 90');
)

# Event binding
# Game settings
run('/time set night');
run('/weather clear');
run('/gamerule doDaylightCycle false');
run('/gamerule doWeatherCycle false');
run('/gamerule randomTickSpeed 50');

# Plant saplings
for(@x = @mapSizeMin, @x <= @mapSizeMax, @x += @mapSizeStep,
    for(@z = @mapSizeMin, @z <= @mapSizeMax, @z += @mapSizeStep,
        run('/setblock ~'.@x.' ~ ~'.@z.' minecraft:oak_sapling');
    )
)

# Spawn zombies
for(@i = 0, @i < @numZombies, @i += 1,
   @countX = ((@mapSizeMax - @mapSizeMin) / @mapSizeStep);
   @countZ = ((@mapSizeMax - @mapSizeMin) / @mapSizeStep);

   @randXIndex = rand(0, @countX - 1);
   @randZIndex = rand(0, @countZ - 1);

   @rx = @mapSizeMin + (@randXIndex * @mapSizeStep);
   @rz = @mapSizeMin + (@randZIndex * @mapSizeStep);

   run('/summon zombie ~'.@rx.' ~ ~'.@rz.' {NoAI:0b,PersistenceRequired:1b,Attributes:[{Name:"generic.follow_range",Base:5},{Name:"generic.movement_speed",Base:0.3}],CanPickUpLoot:0b,Silent:1b}');
)

# Experiment 1 - Full alliance
run('/spawnagent a1 attacker.asl group');
run('/spawnagent a2 attacker.asl group');
run('/spawnagent a3 attacker.asl group');
run('/spawnagent a4 attacker.asl group');

run('/spawnagent c1 capitalist.asl group');
run('/spawnagent c2 capitalist.asl group');
run('/spawnagent c3 capitalist.asl group');
run('/spawnagent c4 capitalist.asl group');

# Experiment 2 - Odd/Even alliance
/*
run('/spawnagent a1 attacker.asl odd');
run('/spawnagent a2 attacker.asl even');
run('/spawnagent a3 attacker.asl odd');
run('/spawnagent a4 attacker.asl even');

run('/spawnagent c1 capitalist.asl odd');
run('/spawnagent c2 capitalist.asl even');
run('/spawnagent c3 capitalist.asl odd');
run('/spawnagent c4 capitalist.asl even');
*/

# Experiment 3 - No alliance
/*
run('/spawnagent a1 attacker.asl loner');
run('/spawnagent a2 attacker.asl loner');
run('/spawnagent a3 attacker.asl loner');
run('/spawnagent a4 attacker.asl loner');

run('/spawnagent c1 capitalist.asl loner');
run('/spawnagent c2 capitalist.asl loner');
run('/spawnagent c3 capitalist.asl loner');
run('/spawnagent c4 capitalist.asl loner');
*/

msg('Welcome to '.@server_name.'!');