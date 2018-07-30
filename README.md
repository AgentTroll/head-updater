# head-updater

Solution to https://www.spigotmc.org/threads/update-entitys-yaw-pitch-each-tick.331592/

This user found that 1) entities do not turn unless their X/Y/Z position gets update as well and 2) that their rotation is very choppy.

I found that the choppiness, to a certain extent, is caused by rounding off of a float from 360 to int 256 to fit into a byte, but on the other hand, some of it is caused by irregular updates to the entity position. The entity position is completely decoupled with how it gets updated to clients, so I force the code path to run by setting a few magic variables before updating the position so the update task will need to update the position as consistently as possible (although it still has a tendency to lag if the server is lagging as well - nothing I can do about that).

There are also 2 hidden functions, the `f(float)` and the `g(float)` methods that update rotation to the same yaw values that cause the entity to turn, so the modified teleport function calls those two methods in order to cause the rotation to update more frequently instead of having to shift the X/Y/Z location of the entity.
