# Project-specific R8 rules. Hilt, Room, Compose and MapLibre ship their own consumer rules.

# The application's Hilt entry point must survive shrinking: Hilt_SpeedometerApp implements it, and without it the app dies at launch.
-keep class com.speedometer.app.SpeedometerApp_GeneratedInjector { *; }
-keep class com.speedometer.app.Hilt_SpeedometerApp { *; }
