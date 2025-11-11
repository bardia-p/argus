@server_name = 'Argus';
@player_name = 'bardibloo'; # change this based on your username
@mapSizeMin = -25;
@mapSizeMax = 25;
@mapSizeStep = 5;
@numZombies = 10;

# Echo server greeting
console(
    'CommandHelper successfully installed! To get started, visit https://methodscript.com, and visit the learning'
    . ' trail at the bottom, and particularly the Beginner\'s and Advanced Guides.'
);

# Event binding
bind('player_join', null, null, @event, @server_name, @player_name, @mapSizeMin, @mapSizeMax, @mapSizeStep, @numZombies,
    # Game settings
    run('/time set night');
    run('/weather clear');
    run('/gamerule doDaylightCycle false');
    run('/gamerule doWeatherCycle false');
    run('/gamemode spectator');
    run('/gamerule randomTickSpeed 25');

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

       run('/summon zombie ~'.@rx.' ~ ~'.@rz.' {NoAI:0b,PersistenceRequired:1b,Attributes:[{Name:"generic.follow_range",Base:100},{Name:"generic.movement_speed",Base:0.5}],CanPickUpLoot:0b,Silent:1b}');
    )
    run('/tp '.@player_name.' ~ ~10 ~ 0 90');

    run('/spawnagent cass hello.asl');
    run('/spawnagent eric hello.asl');
    run('/spawnagent bardia hello.asl');
    run('/spawnagent charissa hello.asl');
    run('/spawnagent daniel hello.asl');

    msg('Welcome to '.@server_name.'!');
)