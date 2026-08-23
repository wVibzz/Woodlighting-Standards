# How Woodlight Standardization Works:
First we start off by detecting portal structures near the player, and when a portal is found, it adds it to a tracker. Then we have to cancel out some vanilla logic, which is around a 5 block radius of the tracked portal.
In this 5 Block Radius we cancel vanilla's `FireBlock.scheduledTick` and `LavaFluid.onRandomTick`

The reason we do this is to cancel the running RNG around the portal for fire spreading both from Lava and Fire Sources. And in return we have to schedule these events our self.
Now while this is good, we've removed the source of RNG, but we need to reimplement this logic but make it controllable.

`FireEventScheduler` is the main engine behind this, we copy vanilla spread and light mechanics and then schedule a series of events.
While doing this for lava was easy, doing the same for fire was much harder and is not quite right still, the challenging part comes from the fire now having to schedule another fire, it has to choose if it burns or spreads or doesn't.

### Now, How exactly does portal/wood lighting work with this new system?

We never actually let fire get placed inside the frame. Instead `PortalLightProbability` works out every tick what the odds were that vanilla would have put fire in each air block inside the portal. We take this info and attribute it to our `Global Counter`, this is a target probability which is randomly chosen between values based on world seed, so you and someone else running the same seed will have the same target.

Now this doesn't mean you can put one lava source and 1 plank next to the portal and call it good, well, you could. But you shouldn't, since this global counter is based on calculated probabilities every tick, this means that the more probable your woodlighting setup is to light a portal in vanilla, means you accumulate probability faster, more fire, and more lava means more chances that fire would have spread into the portal and the more your current progress/counter goes up. If you and someone else build the same setup in the same lava pool at the same spot, your both finishing your woodlight and into the nether at the same time.

### What if we don't do the same setup, position, etc?

We take care of that by making sure orientation doesn't matter. If we seeded off world coordinates, then the same setup built on the other side of a lava pool would derive and give you completely different timings. So `canonicalKey` takes the block's offset from the detected portal, which mirrors and rotates onto the same relative values.

### How pre-portal fires are handled

We simply schedule pre-existing fires to burn out and they don't contribute anything to the global counter or change world state.


### Credits
@ClearColdWater 
@__VY__
@ExerSolver
